package ngo.nabarun.app.businesslogic;

import org.springframework.stereotype.Service;

import ngo.nabarun.app.businesslogic.businessobjects.AccountDetail;
import ngo.nabarun.app.businesslogic.businessobjects.AccountDetailFilter;
import ngo.nabarun.app.businesslogic.businessobjects.Paginate;
import ngo.nabarun.app.businesslogic.businessobjects.TransactionDetail;

@Service
public interface IAccountBL {

	Paginate<AccountDetail> getAccounts(Integer page,Integer size,AccountDetailFilter filter);
	AccountDetail updateAccount(String id,AccountDetail accountDetail) throws Exception;
	Paginate<TransactionDetail> getTransactions(String id, int index, int size);
	AccountDetail createAccount(AccountDetail accountDetail) throws Exception;

}
