package ngo.nabarun.app.businesslogic.domain;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import ngo.nabarun.app.businesslogic.businessobjects.AccountDetail;
import ngo.nabarun.app.businesslogic.businessobjects.AccountDetailFilter;
import ngo.nabarun.app.businesslogic.businessobjects.Paginate;
import ngo.nabarun.app.businesslogic.businessobjects.DonationSummary.PayableAccDetail;
import ngo.nabarun.app.businesslogic.businessobjects.TransactionDetail;
import ngo.nabarun.app.businesslogic.helper.BusinessHelper;
import ngo.nabarun.app.businesslogic.helper.BusinessObjectConverter;
import ngo.nabarun.app.common.enums.AccountStatus;
import ngo.nabarun.app.common.enums.AccountType;
import ngo.nabarun.app.common.enums.IdType;
import ngo.nabarun.app.common.enums.TransactionRefType;
import ngo.nabarun.app.common.enums.TransactionStatus;
import ngo.nabarun.app.common.enums.TransactionType;
import ngo.nabarun.app.common.util.CommonUtils;
import ngo.nabarun.app.infra.dto.AccountDTO;
import ngo.nabarun.app.infra.dto.BankDTO;
import ngo.nabarun.app.infra.dto.TransactionDTO;
import ngo.nabarun.app.infra.dto.UpiDTO;
import ngo.nabarun.app.infra.dto.UserDTO;
import ngo.nabarun.app.infra.dto.AccountDTO.AccountDTOFilter;
import ngo.nabarun.app.infra.dto.TransactionDTO.TransactionDTOFilter;
import ngo.nabarun.app.infra.service.IAccountInfraService;
import ngo.nabarun.app.infra.service.ITransactionInfraService;
import ngo.nabarun.app.infra.service.IUserInfraService;

@Component
public class AccountDO {

	@Autowired
	private ITransactionInfraService transactionInfraService;

	@Autowired
	private IAccountInfraService accountInfraService;

	@Autowired
	protected IUserInfraService userInfraService;

	@Autowired
	protected BusinessHelper businessHelper;

	/**
	 * 
	 * @param page
	 * @param size
	 * @param filter
	 * @return
	 */
	public Paginate<AccountDetail> retrieveAccounts(Integer page, Integer size, AccountDetailFilter filter) {
		AccountDTOFilter filterDTO = null;
		if (filter != null) {
			filterDTO = new AccountDTOFilter();
			filterDTO.setAccountStatus(filter.getStatus());
			filterDTO.setAccountType(filter.getType());
		}

		Page<AccountDetail> pageDetail = accountInfraService.getAccounts(page, size, filterDTO).map(m -> {
			AccountDetail acc = BusinessObjectConverter.toAccountDetail(m);
			if (!filter.isIncludePaymentDetail()) {
				acc.setUpiDetail(null);
				acc.setBankDetail(null);
			}
			if (!filter.isIncludeBalance()) {
				acc.setCurrentBalance(0.0);
			}
			return acc;
		});
		return new Paginate<AccountDetail>(pageDetail);
	}
	
	public AccountDetail retrieveAccount(String id) {
		return BusinessObjectConverter.toAccountDetail(accountInfraService.getAccountDetails(id));
	}

	/**
	 * 
	 * @param type
	 * @return
	 */
	public List<PayableAccDetail> retrievePayableAccounts(AccountType... type) {
		AccountDTOFilter filter = new AccountDTOFilter();
		filter.setAccountStatus(List.of(AccountStatus.ACTIVE));
		filter.setAccountType(List.of(type));
		List<PayableAccDetail> accounts = accountInfraService.getAccounts(null, null, filter).getContent().stream()
				.map(m -> {
					PayableAccDetail pad = new PayableAccDetail();
					pad.setId(m.getId());
					pad.setPayableBankDetails(BusinessObjectConverter.toBankDetail(m.getBankDetail()));
					pad.setPayableUPIDetail(BusinessObjectConverter.toUPIDetail(m.getUpiDetail()));
					return pad;
				}).collect(Collectors.toList());
		return accounts;
	}

	/**
	 * 
	 * @param accountDetail
	 * @param openingBal
	 * @return
	 * @throws Exception
	 */
	public AccountDetail createAccount(AccountDetail accountDetail, Double openingBal) throws Exception {
		UserDTO userDTO = userInfraService.getUser(accountDetail.getAccountHolder().getId(), IdType.ID, false);
		AccountDTO accountDTO = new AccountDTO();
		accountDTO.setId(businessHelper.generateAccountId());
		accountDTO.setProfile(userDTO);
		accountDTO.setAccountName(userDTO.getName());
		if (accountDetail.getBankDetail() != null) {
			BankDTO bankDTO = new BankDTO();
			bankDTO.setAccountHolderName(accountDetail.getBankDetail().getBankAccountHolderName());
			bankDTO.setAccountNumber(accountDetail.getBankDetail().getBankAccountNumber());
			bankDTO.setAccountType(accountDetail.getBankDetail().getBankAccountType());
			bankDTO.setBankName(accountDetail.getBankDetail().getBankName());
			bankDTO.setBranchName(accountDetail.getBankDetail().getBankBranch());
			bankDTO.setIFSCNumber(accountDetail.getBankDetail().getIFSCNumber());
			accountDTO.setBankDetail(bankDTO);
		}

		if (accountDetail.getUpiDetail() != null) {
			UpiDTO upiDTO = new UpiDTO();
			upiDTO.setMobileNumber(accountDetail.getUpiDetail().getMobileNumber());
			upiDTO.setPayeeName(accountDetail.getUpiDetail().getPayeeName());
			upiDTO.setUpiId(accountDetail.getUpiDetail().getUpiId());
			accountDTO.setUpiDetail(upiDTO);
		}
		accountDTO.setAccountType(accountDetail.getAccountType());
		accountDTO.setAccountStatus(AccountStatus.ACTIVE);
		accountDTO.setActivatedOn(CommonUtils.getSystemDate());
		accountDTO.setOpeningBalance(openingBal);
		accountDTO = accountInfraService.createAccount(accountDTO);
		/*
		 * Create transaction and update current value if opening balance is > 0
		 */
		if (accountDTO.getOpeningBalance() > 0) {
			TransactionDetail newTxn = new TransactionDetail();
			newTxn.setTxnAmount(openingBal);
			newTxn.setTxnAmount(accountDTO.getOpeningBalance());
			newTxn.setTxnDate(accountDTO.getActivatedOn());
			newTxn.setTxnRefId(null);
			newTxn.setTxnRefType(TransactionRefType.NONE);
			newTxn.setTxnStatus(TransactionStatus.SUCCESS);
			newTxn.setTxnType(TransactionType.IN);
			newTxn.setTxnDescription("Initial opening balance for account " + accountDTO.getId());
			AccountDetail toAccount = BusinessObjectConverter.toAccountDetail(accountDTO);
			newTxn.setTransferTo(toAccount);
			newTxn = createTransaction(newTxn);
		}
		return BusinessObjectConverter.toAccountDetail(accountDTO);
	}

	/**
	 * 
	 * @param id
	 * @param index
	 * @param size
	 * @return
	 */
	public Paginate<TransactionDetail> retrieveAccountTransactions(String accountId, int index, int size) {
		TransactionDTOFilter filter = new TransactionDTOFilter();
		filter.setAccountId(accountId);
		Page<TransactionDTO> transactions = transactionInfraService.getTransactions(index, size, filter);
		return new Paginate<TransactionDetail>(
				transactions.map(m -> BusinessObjectConverter.toTransactionDetail(m, false)));
	}

	/**
	 * 
	 * @param refId
	 * @param refType
	 * @param status
	 * @return
	 * @throws Exception
	 */
	public List<TransactionDetail> retrieveTransactions(String refId, TransactionRefType refType,
			TransactionStatus status) throws Exception {
		List<TransactionDTO> allTxns = transactionInfraService.getTransactions(refId, refType);
		if (status != null) {
			return allTxns.stream().filter(f -> f.getTxnStatus() == status)
					.map(m -> BusinessObjectConverter.toTransactionDetail(m, true)).collect(Collectors.toList());
		}
		return allTxns.stream().map(m -> BusinessObjectConverter.toTransactionDetail(m, true))
				.collect(Collectors.toList());
	}

	/**
	 * 
	 * @param transaction
	 * @return
	 * @throws Exception
	 */
	public TransactionDetail createTransaction(TransactionDetail transaction) throws Exception {
		/*
		 * Checking for any existing transactions created against the ref id if one or
		 * more transaction exists then checking if any of the transaction status is
		 * success or not create a transaction with success status if none of then is
		 * success
		 */
		List<TransactionDetail> successTxn = retrieveTransactions(transaction.getTxnRefId(),
				transaction.getTxnRefType(), TransactionStatus.SUCCESS);

		if (successTxn.isEmpty()) {
			TransactionDTO newTxn = generateNewTxn(transaction);
			return BusinessObjectConverter.toTransactionDetail(newTxn, true);
		}
		return successTxn.get(0);
	}

	private TransactionDTO generateNewTxn(TransactionDetail transaction) throws Exception {
		TransactionDTO newTxn = new TransactionDTO();
		newTxn.setId(businessHelper.generateTransactionId());
		newTxn.setTxnAmount(transaction.getTxnAmount());
		newTxn.setTxnDate(transaction.getTxnDate());
		newTxn.setTxnRefId(transaction.getTxnRefId());
		newTxn.setTxnRefType(transaction.getTxnRefType());
		newTxn.setTxnStatus(transaction.getTxnStatus());
		newTxn.setTxnType(transaction.getTxnType());
		newTxn.setTxnDescription(transaction.getTxnDescription());
		if (transaction.getTxnType() == TransactionType.IN) {
			AccountDTO destAccDTO = new AccountDTO();
			destAccDTO.setId(transaction.getTransferTo().getId());
			destAccDTO.setAccountName(transaction.getTransferTo().getAccountHolderName());
			newTxn.setToAccount(destAccDTO);
		} else if (transaction.getTxnType() == TransactionType.OUT) {
			AccountDTO srcAccDTO = new AccountDTO();
			srcAccDTO.setId(transaction.getTransferFrom().getId());
			srcAccDTO.setAccountName(transaction.getTransferFrom().getAccountHolderName());
			newTxn.setToAccount(srcAccDTO);
		} else {
			AccountDTO destAccDTO = new AccountDTO();
			destAccDTO.setId(transaction.getTransferTo().getId());
			destAccDTO.setAccountName(transaction.getTransferTo().getAccountHolderName());
			newTxn.setToAccount(destAccDTO);
			AccountDTO srcAccDTO = new AccountDTO();
			srcAccDTO.setId(transaction.getTransferFrom().getId());
			srcAccDTO.setAccountName(transaction.getTransferFrom().getAccountHolderName());
			newTxn.setToAccount(srcAccDTO);
		}

		return transactionInfraService.createTransaction(newTxn);

	}

	public void revertTransaction(String refId, TransactionRefType refType, TransactionStatus status)
			throws Exception {
		List<TransactionDetail> transactions = retrieveTransactions(refId, refType, status);

		for (TransactionDetail transaction : transactions) {
			if(transaction.getTxnStatus() == TransactionStatus.SUCCESS) {
				TransactionDetail newTxn = new TransactionDetail();
				newTxn.setTxnAmount(transaction.getTxnAmount());
				newTxn.setTxnDate(transaction.getTxnDate());
				newTxn.setTxnRefId(transaction.getTxnRefId());
				newTxn.setTxnRefType(transaction.getTxnRefType());
				newTxn.setTxnStatus(TransactionStatus.SUCCESS);
				newTxn.setTxnDescription("Reverting last transaction " + transaction.getTxnId());

				if (transaction.getTxnType() == TransactionType.IN) {
					newTxn.setTxnType(TransactionType.OUT);
					newTxn.setTransferFrom(transaction.getTransferTo());
				}
				else if (transaction.getTxnType() == TransactionType.OUT) {
					newTxn.setTxnType(TransactionType.IN);
					newTxn.setTransferTo(transaction.getTransferFrom());
				}else {
					newTxn.setTxnType(TransactionType.OUT);
					newTxn.setTransferFrom(transaction.getTransferTo());
					newTxn.setTxnType(TransactionType.IN);
					newTxn.setTransferTo(transaction.getTransferFrom());
				}
				generateNewTxn(newTxn);
			}
		}
	}

}
