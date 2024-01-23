package ngo.nabarun.app.infra.serviceimpl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ngo.nabarun.app.common.enums.TransactionRefType;
import ngo.nabarun.app.common.enums.TransactionStatus;
import ngo.nabarun.app.common.enums.TransactionType;
import ngo.nabarun.app.common.util.CommonUtils;
import ngo.nabarun.app.infra.core.entity.AccountEntity;
import ngo.nabarun.app.infra.core.entity.TransactionEntity;
import ngo.nabarun.app.infra.core.repo.AccountRepository;
import ngo.nabarun.app.infra.core.repo.TransactionRepository;
import ngo.nabarun.app.infra.dto.AccountDTO;
import ngo.nabarun.app.infra.dto.BankDTO;
import ngo.nabarun.app.infra.dto.TransactionDTO;
import ngo.nabarun.app.infra.dto.UpiDTO;
import ngo.nabarun.app.infra.misc.InfraDTOHelper;
import ngo.nabarun.app.infra.service.IAccountInfraService;
import ngo.nabarun.app.infra.service.ITransactionInfraService;


@Service
public class PaymentsInfraServiceImpl implements ITransactionInfraService,IAccountInfraService{

	@Autowired
	private AccountRepository accRepo;
	
	@Autowired
	private TransactionRepository txnRepo;
	
	@Override
	public TransactionDTO createTransaction(TransactionDTO transactionDTO) throws Exception {
		//if status is given as success then and update link account
		TransactionEntity txn = new TransactionEntity();
		txn.setId(transactionDTO.getId());
		txn.setComment(transactionDTO.getComment());
		txn.setCreationDate(CommonUtils.getSystemDate());
		txn.setStatus(transactionDTO.getTxnStatus() == null ? null : transactionDTO.getTxnStatus().name());
		txn.setTransactionType(transactionDTO.getTxnType() == null ? null : transactionDTO.getTxnType().name());
		txn.setTransactionAmt(transactionDTO.getTxnAmount());
		txn.setTransactionDate(transactionDTO.getTxnDate());
		txn.setTransactionRefId(transactionDTO.getTxnRefId());
		txn.setTransactionRefType(transactionDTO.getTxnRefType() == null ? null : transactionDTO.getTxnRefType().name());	
		txn.setTransactionDescription(transactionDTO.getTxnDescription());;
		
		AccountEntity srcAccount=null;
		AccountEntity destAccount=null;
		if (transactionDTO.getTxnStatus() == TransactionStatus.SUCCESS) {
			if (transactionDTO.getTxnType() == TransactionType.IN) {
				/*
				 * updating current balance
				 */
				destAccount= accRepo.findById(transactionDTO.getToAccount().getId()).orElseThrow();
				destAccount.setCurrentBalance(destAccount.getCurrentBalance() + transactionDTO.getTxnAmount());
				destAccount=accRepo.save(destAccount);
				txn.setToAccBalAfterTxn(destAccount.getCurrentBalance());
				txn.setToAccount(transactionDTO.getToAccount().getId());
			} else if (transactionDTO.getTxnType() == TransactionType.OUT) {
				srcAccount= accRepo.findById(transactionDTO.getFromAccount().getId()).orElseThrow();
				srcAccount.setCurrentBalance(srcAccount.getCurrentBalance() - transactionDTO.getTxnAmount());
				srcAccount=accRepo.save(srcAccount);
				txn.setFromAccBalAfterTxn(srcAccount.getCurrentBalance());
				txn.setFromAccount(transactionDTO.getFromAccount().getId());
			} else if (transactionDTO.getTxnType() == TransactionType.TRANSFER) {
				srcAccount= accRepo.findById(transactionDTO.getFromAccount().getId()).orElseThrow();
				srcAccount.setCurrentBalance(srcAccount.getCurrentBalance() - transactionDTO.getTxnAmount());
				srcAccount=accRepo.save(srcAccount);
				txn.setFromAccBalAfterTxn(srcAccount.getCurrentBalance());
				txn.setFromAccount(transactionDTO.getFromAccount().getId());
				
				destAccount= accRepo.findById(transactionDTO.getToAccount().getId()).orElseThrow();
				destAccount.setCurrentBalance(destAccount.getCurrentBalance() + transactionDTO.getTxnAmount());
				destAccount=accRepo.save(destAccount);
				txn.setToAccBalAfterTxn(destAccount.getCurrentBalance());
				txn.setToAccount(transactionDTO.getToAccount().getId());
			}
		}
		return InfraDTOHelper.convertToTransactionDTO(txnRepo.save(txn), srcAccount, destAccount);
	}

	@Override
	public List<TransactionDTO> getTransactions(String txnRefNumber, TransactionRefType txnRefType) throws Exception {
		List<TransactionEntity> txnList=txnRepo.findByTransactionRefIdAndTransactionRefType(txnRefNumber,txnRefType.name());
		return txnList.stream().map(m->InfraDTOHelper.convertToTransactionDTO(m,null,null)).toList();
	}

	@Override
	public AccountDTO getAccountDetails(String id) {
		AccountEntity accountInfo= accRepo.findById(id).orElseThrow();
		return InfraDTOHelper.convertToAccountDTO(accountInfo,null);
	}

	@Override
	public List<AccountDTO> getAccounts() {
		List<AccountEntity> accounts=accRepo.findAll(); 
		return accounts.stream().map(m->InfraDTOHelper.convertToAccountDTO(m,null)).toList();
	}

	@Override
	public AccountDTO createAccount(AccountDTO accountDTO) {
		AccountEntity entity = new AccountEntity();
		entity.setId(accountDTO.getId());
		entity.setAccountName(accountDTO.getAccountName());
		entity.setAccountStatus(accountDTO.getAccountStatus() == null ? null : accountDTO.getAccountStatus().name());
		entity.setAccountType(accountDTO.getAccountType() == null ? null : accountDTO.getAccountType().name());
		entity.setActivatedOn(accountDTO.getActivatedOn());
		entity.setCreatedOn(CommonUtils.getSystemDate());
		entity.setOpeningBalance(accountDTO.getOpeningBalance());
		entity.setCurrentBalance(0.0);
		entity.setProfile(accountDTO.getProfile().getProfileId());
		if(accountDTO.getBankDetail() != null) {
			BankDTO bankDetail=accountDTO.getBankDetail();
			entity.setBankAccountHolderName(bankDetail.getAccountHolderName());
			entity.setBankAccountNumber(bankDetail.getAccountNumber());
			entity.setBankAccountType(bankDetail.getAccountType());
			entity.setBankBranchName(bankDetail.getBranchName());
			entity.setBankIFSCNumber(bankDetail.getIFSCNumber());
			entity.setBankName(bankDetail.getBankName());
		}
		
		
		if(accountDTO.getUpiDetail() != null) {
			UpiDTO upiDetail=accountDTO.getUpiDetail();
			entity.setUpiId(upiDetail.getUpiId());
			entity.setUpiMobileNumber(upiDetail.getMobileNumber());
			entity.setUpiPayeeName(upiDetail.getPayeeName());
		}
	
		return InfraDTOHelper.convertToAccountDTO(accRepo.save(entity),null);
	}

	@Override
	public List<TransactionDTO> getTransactionsForAccount(String id) {
		List<TransactionEntity> a=txnRepo.findByFromAccountOrToAccount(id, id);		
		System.err.println(a);
		return a.stream().map(m->InfraDTOHelper.convertToTransactionDTO(m,null, null)).toList();
	}

	

}