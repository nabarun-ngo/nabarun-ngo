package ngo.nabarun.app.businesslogic.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import ngo.nabarun.app.businesslogic.businessobjects.AccountDetail;
import ngo.nabarun.app.businesslogic.businessobjects.AdditionalField;
import ngo.nabarun.app.businesslogic.businessobjects.DocumentDetail;
import ngo.nabarun.app.businesslogic.businessobjects.DonationDetail;
import ngo.nabarun.app.businesslogic.businessobjects.DonationDetailFilter;
import ngo.nabarun.app.businesslogic.businessobjects.DonationSummary;
import ngo.nabarun.app.businesslogic.businessobjects.Paginate;
import ngo.nabarun.app.businesslogic.businessobjects.TransactionDetail;
import ngo.nabarun.app.businesslogic.helper.BusinessConstants;
import ngo.nabarun.app.businesslogic.helper.BusinessObjectConverter;
import ngo.nabarun.app.common.enums.AccountType;
import ngo.nabarun.app.common.enums.AdditionalFieldSource;
import ngo.nabarun.app.common.enums.DocumentIndexType;
import ngo.nabarun.app.common.enums.DonationStatus;
import ngo.nabarun.app.common.enums.DonationType;
import ngo.nabarun.app.common.enums.EmailRecipientType;
import ngo.nabarun.app.common.enums.IdType;
import ngo.nabarun.app.common.enums.PaymentMethod;
import ngo.nabarun.app.common.enums.TransactionRefType;
import ngo.nabarun.app.common.enums.TransactionStatus;
import ngo.nabarun.app.common.enums.TransactionType;
import ngo.nabarun.app.common.util.CommonUtils;
import ngo.nabarun.app.infra.dto.AccountDTO;
import ngo.nabarun.app.infra.dto.CorrespondentDTO;
import ngo.nabarun.app.infra.dto.DonationDTO;
import ngo.nabarun.app.infra.dto.FieldDTO;
import ngo.nabarun.app.infra.dto.UserDTO;
import ngo.nabarun.app.infra.dto.DonationDTO.DonationDTOFilter;
import ngo.nabarun.app.infra.service.IDocumentInfraService;
import ngo.nabarun.app.infra.service.IDonationInfraService;

@Component
public class DonationDO extends AccountDO {

	@Autowired
	private IDonationInfraService donationInfraService;

	@Autowired
	private IDocumentInfraService documentInfraService;

	public Paginate<DonationDetail> retrieveDonations(Integer index, Integer size, DonationDetailFilter filter) {
		DonationDTOFilter filterDTO = null;
		if (filter != null) {
			filterDTO = new DonationDTOFilter();
			filterDTO.setDonationId(filter.getDonationId());
			filterDTO.setDonationStatus(filter.getDonationStatus());
			filterDTO.setDonationType(filter.getDonationType());
			filterDTO.setDonorName(filter.getDonorName());
			filterDTO.setFromDate(filter.getFromDate());
			filterDTO.setToDate(filter.getToDate());
			filterDTO.setIsGuestDonation(filter.getIsGuest());
			filterDTO.setPaidAccountId(filter.getPaidToAccountId());
		}
		Page<DonationDetail> page = donationInfraService.getDonations(index, size, filterDTO)
				.map(BusinessObjectConverter::toDonationDetail);
		return new Paginate<DonationDetail>(page);
	}

	public Paginate<DonationDetail> retrieveUserDonations(Integer index, Integer size, String id, IdType idType)
			throws Exception {
		UserDTO userDTO = userInfraService.getUser(id, idType, false);
		DonationDTOFilter filterDTO = new DonationDTOFilter();
		filterDTO.setDonorId(userDTO.getProfileId());
		Page<DonationDetail> donationPage = donationInfraService.getDonations(index, size, filterDTO).map(m -> {
			m.setDonor(userDTO);
			return BusinessObjectConverter.toDonationDetail(m);
		});
		return new Paginate<DonationDetail>(donationPage);
	}

	public List<DocumentDetail> retrieveDonationDocument(String donationId) {
		return documentInfraService.getDocumentList(donationId, DocumentIndexType.DONATION).stream().map(m -> {
			DocumentDetail doc = new DocumentDetail();
			doc.setDocId(m.getDocId());
			doc.setDocumentIndexId(donationId);
			doc.setImage(m.isImage());
			doc.setOriginalFileName(m.getOriginalFileName());
			return doc;
		}).toList();
	}

	public DonationDetail raiseDonation(DonationDetail donationDetail) throws Exception {
		DonationDTO donationDTO = businessHelper.convertToDonationDTO(donationDetail.getDonationType());
		
		UserDTO donor = new UserDTO();

		if (donationDetail.getIsGuest() == Boolean.TRUE) {
			donationDTO.setId(businessHelper.generateDonationId());
			donationDTO.setAmount(donationDetail.getAmount());
			donationDTO.setType(DonationType.ONETIME);
			donationDTO.setGuest(true);
			//donationDTO.setStatus(DonationStatus.RAISED);
			donor.setName(donationDetail.getDonorDetails().getFullName());
			donor.setEmail(
					donationDetail.getDonorDetails() == null ? null : donationDetail.getDonorDetails().getEmail());
			donor.setPhoneNumber(donationDetail.getDonorDetails() == null ? null
					: donationDetail.getDonorDetails().getPrimaryNumber());
			donationDTO.setDonor(donor);
			donationDTO.setForEventId(donationDetail.getEvent() == null ? null : donationDetail.getEvent().getId());

		} else {
			donationDTO.setId(businessHelper.generateDonationId());
			//donationDTO.setAmount(donationDetail.getAmount());
			donationDTO.setDonor(donor);
			donationDTO.setForEventId(
					donationDetail.getEvent() != null && donationDetail.getDonationType() == DonationType.ONETIME
							? donationDetail.getEvent().getId()
							: null);

			donationDTO.setGuest(false);
			donationDTO.setStartDate(
					donationDetail.getDonationType() == DonationType.REGULAR ? donationDetail.getStartDate() : null);
			donationDTO.setEndDate(
					donationDetail.getDonationType() == DonationType.REGULAR ? donationDetail.getEndDate() : null);
			//donationDTO.setStatus(DonationStatus.RAISED);//automatically come from configuration
			donationDTO.setType(donationDetail.getDonationType());
			donationDTO.setDonor(donor);
		}
		/**
		 * Custom field
		 */
		if (donationDetail.getAdditionalFields() != null) {
			List<FieldDTO> fieldDTO = new ArrayList<>();
			for (AdditionalField addfield : donationDetail.getAdditionalFields()) {
				fieldDTO.add(
						businessHelper.findAddtlFieldAndConvertToFieldDTO(AdditionalFieldSource.DONATION, addfield));
			}
			donationDTO.setAdditionalFields(fieldDTO);
		}
		donationDTO = donationInfraService.createDonation(donationDTO);

		CorrespondentDTO recipient = CorrespondentDTO.builder().name(donor.getName())
				.emailRecipientType(EmailRecipientType.TO).email(donor.getEmail()).mobile(donor.getPhoneNumber())
				.build();
		businessHelper.sendEmail(BusinessConstants.EMAILTEMPLATE__DONATION_CREATE_REGULAR, List.of(recipient),
				Map.of("user", donor, "donation", donationDTO));
		return BusinessObjectConverter.toDonationDetail(donationDTO, null, null);
	}


	public boolean checkIfDonationRaised(String userId, Date startDate, Date endDate) {
		DonationDTOFilter filterDTO = new DonationDTOFilter();
		filterDTO.setDonorId(userId);
		filterDTO.setDonationType(List.of(DonationType.REGULAR));

		List<DonationDTO> donations = donationInfraService.getDonations(null, null, filterDTO).getContent();
		return donations.stream().filter(c -> c.getStatus() != DonationStatus.CANCELLED)
				.anyMatch(contribution -> (startDate.compareTo(contribution.getEndDate()) < 0
						|| startDate.compareTo(contribution.getEndDate()) == 0)
						&& (endDate.compareTo(contribution.getStartDate()) > 0
								|| endDate.compareTo(contribution.getStartDate()) == 0));

	}

	public DonationDetail updateDonation(DonationDetail donation, DonationDetail request, String loggedInUserId,
			AccountDetail paidTo) throws Exception {

		DonationDTO updatedDetail = new DonationDTO();
		updatedDetail.setAmount(request.getAmount());
		updatedDetail.setStatus(request.getDonationStatus());

		if (donation.getIsGuest() == Boolean.TRUE) {
			UserDTO donor = new UserDTO();
			donor.setName(request.getDonorDetails() == null ? null : request.getDonorDetails().getFullName());
			donor.setEmail(request.getDonorDetails() == null ? null : request.getDonorDetails().getEmail());
			donor.setPhoneNumber(
					request.getDonorDetails() == null ? null : request.getDonorDetails().getPrimaryNumber());
			updatedDetail.setDonor(donor);
		}

		if (request.getDonationStatus() == DonationStatus.PAID) {
			updatedDetail.setPaidOn(request.getPaidOn());
			updatedDetail.setPaymentMethod(request.getPaymentMethod());
			updatedDetail
					.setUpiName(request.getPaymentMethod() == PaymentMethod.UPI ? request.getPaidUsingUPI() : null);

			UserDTO auth_user = userInfraService.getUser(loggedInUserId, IdType.AUTH_USER_ID, false);
			updatedDetail.setConfirmedBy(auth_user);
			updatedDetail.setConfirmedOn(CommonUtils.getSystemDate());

			TransactionDetail newTxn = new TransactionDetail();
			newTxn.setTransferTo(paidTo);
			newTxn.setTxnAmount(donation.getAmount());
			newTxn.setTxnDate(request.getPaidOn());
			newTxn.setTxnRefId(donation.getId());
			newTxn.setTxnRefType(TransactionRefType.DONATION);
			newTxn.setTxnStatus(TransactionStatus.SUCCESS);
			newTxn.setTxnType(TransactionType.IN);
			newTxn.setTxnDescription("Donation amount for id " + donation.getId());
			newTxn = createTransaction(newTxn);

			updatedDetail.setTransactionRefNumber(newTxn.getTxnId());

			AccountDTO accDTO = new AccountDTO();
			accDTO.setAccountName(paidTo.getAccountHolderName());
			accDTO.setId(paidTo.getId());
			updatedDetail.setPaidToAccount(accDTO);
			updatedDetail.setComment(request.getRemarks());

		} else if (request.getDonationStatus() == DonationStatus.PAYMENT_FAILED) {
			TransactionDetail newTxn = new TransactionDetail();
			newTxn.setTxnAmount(donation.getAmount());
			newTxn.setTxnDate(request.getPaidOn());
			newTxn.setTxnRefId(donation.getId());
			newTxn.setTxnRefType(TransactionRefType.DONATION);
			newTxn.setTxnStatus(TransactionStatus.FAILURE);
			newTxn.setTxnType(TransactionType.IN);
			newTxn.setComment(request.getPaymentFailureDetail());
			newTxn.setTxnDescription("Donation amount for id " + donation.getId());
			newTxn = createTransaction(newTxn);
			updatedDetail.setTransactionRefNumber(newTxn.getTxnId());
			updatedDetail.setPaymentFailDetail(request.getPaymentFailureDetail());

		} else if (request.getDonationStatus() == DonationStatus.PAY_LATER) {
			updatedDetail.setPayLaterReason(request.getLaterPaymentReason());
		} else if (request.getDonationStatus() == DonationStatus.CANCELLED) {
			updatedDetail.setCancelReason(request.getCancelletionReason());
		}
		updatedDetail = donationInfraService.updateDonation(donation.getId(), updatedDetail);
		return BusinessObjectConverter.toDonationDetail(updatedDetail, null, null);
	}

	public DonationSummary retrieveDonationSummary(String id, boolean includePayableAcc) throws Exception {
		List<DonationStatus> outStatus = businessHelper.getOutstandingDonationStatus();
		DonationDTOFilter filterDTO = new DonationDTOFilter();
		filterDTO.setDonorId(id);
		filterDTO.setDonationStatus(outStatus);
		List<DonationDTO> outDons = donationInfraService.getDonations(null, null, filterDTO).getContent();
		DonationSummary dsmry = new DonationSummary();

		dsmry.setOutstandingAmount(outDons.stream().mapToDouble(DonationDTO::getAmount).sum());
		dsmry.setHasOutstanding(dsmry.getOutstandingAmount() > 0);

		List<String> months = new ArrayList<>();
		for (DonationDTO outDon : outDons) {
			if (outDon.getType() != DonationType.ONETIME) {
				months.addAll(CommonUtils.getMonthsBetween(outDon.getStartDate(), outDon.getEndDate()));
			}
		}
		dsmry.setOutstandingMonths(months);

		if (includePayableAcc) {
			dsmry.setPayableAccounts(retrievePayableAccounts(AccountType.DONATION));
		}

		return dsmry;
	}

	public DonationDetail retrieveDonation(String id) {
		return BusinessObjectConverter.toDonationDetail(donationInfraService.getDonation(id));
	}

	
}
