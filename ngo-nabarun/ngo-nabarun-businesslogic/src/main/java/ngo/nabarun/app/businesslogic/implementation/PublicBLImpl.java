package ngo.nabarun.app.businesslogic.implementation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ngo.nabarun.app.businesslogic.IPublicBL;
import ngo.nabarun.app.businesslogic.businessobjects.InterviewDetail;
import ngo.nabarun.app.businesslogic.businessobjects.InterviewDetail.UserAction;
import ngo.nabarun.app.businesslogic.businessobjects.AdditionalField;
import ngo.nabarun.app.businesslogic.businessobjects.DonationSummary.PayableAccDetail;
import ngo.nabarun.app.businesslogic.businessobjects.KeyValue;
import ngo.nabarun.app.businesslogic.businessobjects.RequestDetail;
import ngo.nabarun.app.businesslogic.businessobjects.UPIDetail;
import ngo.nabarun.app.businesslogic.businessobjects.UserDetail;
import ngo.nabarun.app.businesslogic.businessobjects.UserDetailFilter;
import ngo.nabarun.app.businesslogic.domain.AccountDO;
import ngo.nabarun.app.businesslogic.domain.RequestDO;
import ngo.nabarun.app.businesslogic.exception.BusinessException.ExceptionEvent;
import ngo.nabarun.app.businesslogic.helper.BusinessConstants;
import ngo.nabarun.app.common.enums.AccountType;
import ngo.nabarun.app.common.enums.AdditionalFieldKey;
import ngo.nabarun.app.common.enums.EmailRecipientType;
import ngo.nabarun.app.common.enums.RoleCode;
import ngo.nabarun.app.common.enums.WorkflowType;
import ngo.nabarun.app.common.util.CommonUtils;
import ngo.nabarun.app.common.util.PasswordUtils;
import ngo.nabarun.app.infra.dto.CorrespondentDTO;

@Service
public class PublicBLImpl extends BaseBLImpl implements IPublicBL {

	@Autowired
	private AccountDO accountDO;

	@Autowired
	private RequestDO requestDO;

	@Override
	public Map<String, Object> getPageData() {
		Map<String, Object> pageDataMap = new HashMap<>();
		try {
			UserDetailFilter filter= new UserDetailFilter();
			filter.setPublicFlag(true);
			List<UserDetail> users = userDO.retrieveAllUsers(null, null, filter).getContent();
			pageDataMap.put("profiles", users);
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			for (KeyValue keyValue : businessHelper.getNabarunOrgInfo()) {
				pageDataMap.put(keyValue.getKey(), keyValue.getValue());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return pageDataMap;

	}

	@Override
	public InterviewDetail signUp(InterviewDetail interview) throws Exception {

		if (interview.getBreadCrumb().size() == 0) {
			interview.getBreadCrumb().add("Home");
		}
		if (interview.getActionName() == UserAction.SUBMIT_BASIC_INFO) {
			List<String> rules = businessHelper.getRules().stream().map(m -> m.getValue()).toList();
			interview.setRules(rules);
			interview.setStage("1");
			interview.getBreadCrumb().add("Rules and Regulations");
		} else if (interview.getActionName() == UserAction.ACCEPT_RULES) {
			String description = businessHelper.getPasswordPolicyDescription(userDO.getPasswordPolicy());
			interview.setMessage(description);
			interview.setStage("2");
			interview.getBreadCrumb().add("Login Details");
		} else if (interview.getActionName() == UserAction.SUBMIT_LOGIN_DETAIL) {
			String name = interview.getPassword();
			String email = interview.getEmail();
			String mobileNo = interview.getDialCode() + interview.getContactNumber();
			String password = interview.getPassword();

			businessHelper.throwBusinessExceptionIf(() -> userDO.isUserExists(email),
					ExceptionEvent.EMAIL_ALREADY_IN_USE);

			String passwordRegex = businessHelper.getPasswordPolicyRegex(userDO.getPasswordPolicy());
			businessHelper.throwBusinessExceptionIf(() -> PasswordUtils.isPasswordValid(password, passwordRegex),
					ExceptionEvent.PASSWORD_NOT_COMPLIANT);

			String token = businessHelper.sendOTP(name, email, mobileNo, "Sign up", null);
			interview.setMessage("One Time Password has been sent to " + email);
			interview.setOtpToken(token);
			interview.setSiteKey(propertyHelper.getGoogleRecaptchaSiteKey());
			interview.setStage("3");
			interview.getBreadCrumb().add("Verify and Submit");
		} else if (interview.getActionName() == UserAction.RESEND_OTP) {
			businessHelper.reSendOTP(interview.getOtpToken());
			interview.setMessage("One Time Password has been sent to " + interview.getEmail());
			interview.setStage("3");
			interview.getBreadCrumb().add("Verify and Submit");
		} else if (interview.getActionName() == UserAction.SUBMIT_OTP) {
			businessHelper.validateOTP(interview.getOtpToken(), interview.getOnetimePassword(), "Sign up");
			List<AdditionalField> addFieldList = new ArrayList<>();
			addFieldList.add(new AdditionalField(AdditionalFieldKey.firstName, interview.getFirstName(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.lastName, interview.getLastName(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.email, interview.getEmail(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.dialCode, interview.getDialCode(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.mobileNumber, interview.getContactNumber(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.hometown, interview.getHometown(), true));
			addFieldList.add(
					new AdditionalField(AdditionalFieldKey.reasonForJoining, interview.getReasonForJoining(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.howDoUKnowAboutNabarun,
					interview.getHowDoUKnowAboutNabarun(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.password, interview.getPassword(), false, true));

			RequestDetail request = new RequestDetail();
			request.setDescription(
					"I want to join NABARUN for " + interview.getReasonForJoining() + ". Please do needful.");
			request.setAdditionalFields(addFieldList);
			request.setType(WorkflowType.JOIN_REQUEST);
			request.setDelegated(false);

			UserDetail requester = new UserDetail();
			requester.setFirstName(interview.getFirstName());
			requester.setLastName(interview.getLastName());
			requester.setEmail(interview.getEmail());
			request.setRequester(requester);

			request = requestDO.createRequest(request, true, null, (t, u) -> {
				return performWorkflowAction(t, u);
			});

			interview.setStage("POST_SUBMIT");
			interview.getBreadCrumb().add("Request Submitted");
			interview.setMessage("Thank you for your interest. Your request number is " + request.getId()
					+ ". We will connect you very shortly.");
		}
		return interview;
	}

	@Override
	public InterviewDetail initDonation(InterviewDetail interview) throws Exception {
		if (interview.getBreadCrumb().size() == 0) {
			interview.getBreadCrumb().add("Home");
		}
		if (interview.getActionName() == UserAction.SUBMIT_PAYMENT_INFO) {
			List<PayableAccDetail> accounts = accountDO.retrievePayableAccounts(AccountType.PUBLIC_DONATION).stream()
					.map(m -> {
						if (m.getPayableUPIDetail() != null) {
							UPIDetail upiDet = m.getPayableUPIDetail();
							upiDet.setQrData(CommonUtils.getUPIURI(upiDet.getUpiId(), upiDet.getPayeeName(),
									interview.getAmount(), null, null, null));
						}
						return m;
					}).collect(Collectors.toList());
			interview.setAccounts(accounts);
			interview.setStage("MAKE_PAYMENT");
			interview.getBreadCrumb().add("Make Payment");
		} else if (interview.getActionName() == UserAction.CONFIRM_PAYMENT) {
			List<AdditionalField> addFieldList = new ArrayList<>();
			addFieldList.add(new AdditionalField(AdditionalFieldKey.name, interview.getFirstName(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.email, interview.getEmail(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.dialCode, interview.getDialCode(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.mobileNumber, interview.getContactNumber(), true));
			addFieldList
					.add(new AdditionalField(AdditionalFieldKey.amount, String.valueOf(interview.getAmount()), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.paymentMethod, interview.getPaymentMethod(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.paidToAccount, interview.getPaidToAccountId(), true));
			System.out.println("Hii "+interview.getPaidToAccountId());

			RequestDetail request = new RequestDetail();
			request.setDescription("Please check and confirm payment.");
			request.setAdditionalFields(addFieldList);
			request.setType(WorkflowType.CHECK_PAYMENT);
			request.setDelegated(false);

			UserDetail requester = new UserDetail();
			requester.setFirstName(interview.getFirstName());
			requester.setLastName("");
			requester.setEmail(interview.getEmail());
			request.setRequester(requester);

			request = requestDO.createRequest(request, true, null, (t, u) -> {
				return performWorkflowAction(t, u);
			});

			interview.setStage("POST_SUBMIT");
			interview.getBreadCrumb().add("Payment Completed");
			interview.setMessage("We will check and confirm about this payment soonest.");

		} else if (interview.getActionName() == UserAction.SUBMIT_REQUEST) {
			List<AdditionalField> addFieldList = new ArrayList<>();
			addFieldList.add(new AdditionalField(AdditionalFieldKey.name, interview.getFirstName(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.email, interview.getEmail(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.dialCode, interview.getDialCode(), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.mobileNumber, interview.getContactNumber(), true));
			addFieldList
					.add(new AdditionalField(AdditionalFieldKey.amount, String.valueOf(interview.getAmount()), true));
			addFieldList.add(new AdditionalField(AdditionalFieldKey.paymentMethod, interview.getPaymentMethod(), true));

			RequestDetail request = new RequestDetail();
			request.setDescription("Please collect cash payment.");
			request.setAdditionalFields(addFieldList);
			request.setType(WorkflowType.COLLECT_CASH_PAYMENT);
			request.setDelegated(false);

			UserDetail requester = new UserDetail();
			requester.setFirstName(interview.getFirstName());
			requester.setLastName("");
			requester.setEmail(interview.getEmail());
			request.setRequester(requester);

			request = requestDO.createRequest(request, true, null, (t, u) -> {
				return performWorkflowAction(t, u);
			});

			interview.setStage("POST_SUBMIT");
			interview.getBreadCrumb().add("Request Submitted");
			interview.setMessage("We will connect very shortly to collect your cash payment.");
		}
		return interview;
	}

	@Override
	public InterviewDetail contact(InterviewDetail interview) throws Exception {
		if (interview.getBreadCrumb().size() == 0) {
			interview.getBreadCrumb().add("Home");
		}
		List<CorrespondentDTO> recipients = new ArrayList<>();
		Optional<KeyValue> orgEmail = businessHelper.getNabarunOrgInfo().stream()
				.filter(f -> "EMAIL_ADDRESS".equalsIgnoreCase(f.getKey())).findFirst();
		if (!orgEmail.isEmpty()) {
			recipients.add(CorrespondentDTO.builder().emailRecipientType(EmailRecipientType.TO)
					.email(orgEmail.get().getValue()).build());
		}

		recipients
				.add(CorrespondentDTO.builder().emailRecipientType(EmailRecipientType.CC).name(interview.getFirstName())
						.email(interview.getEmail()).mobile(interview.getContactNumber()).build());
		List<UserDetail> users = userDO.getUsers(
				List.of(RoleCode.PRESIDENT, RoleCode.VICE_PRESIDENT, RoleCode.SECRETARY, RoleCode.ASST_SECRETARY,
						RoleCode.GROUP_COORDINATOR, RoleCode.ASST_GROUP_COORDINATOR, RoleCode.TECHNICAL_SPECIALIST));
		for (UserDetail user : users) {
			recipients.add(CorrespondentDTO.builder().emailRecipientType(EmailRecipientType.BCC).name(user.getFullName())
					.email(user.getEmail()).mobile(user.getPrimaryNumber()).build());
		}
		businessHelper.sendEmail(interview.getFirstName(), BusinessConstants.EMAILTEMPLATE__PUBLIC_QUERY, recipients,
				Map.of("interview", interview));
		interview.setStage("POST_SUBMIT");
		interview.getBreadCrumb().add("Request Submitted");
		interview.setMessage("We have acknowledged your request (Request Id : " + interview.getId()
				+ "). We will connect you shortly.");
		return interview;
	}
}
