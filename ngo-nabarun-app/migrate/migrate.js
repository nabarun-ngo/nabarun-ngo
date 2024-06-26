var fs = require('fs');
print("starting ...")
var obj = JSON.parse(fs.readFileSync(__dirname+'\\config.json', 'utf8'));
var sourceDB= db.getSiblingDB(obj.SOURCE_DB)

/**
* DONATION 
*/

print("migrating donations ...")
const sourceDonations =sourceDB.contributions.find();
const don_mig_info=db.migration_info.findOne({_id:"mig-donation-info"});
let donations=[];
while(sourceDonations.hasNext()){
	let donation=sourceDonations.next()
	if(don_mig_info == null || donation.raisedOn > don_mig_info.last_migration){
		donations.push(convertDonations(donation))
	}
}
if(donations.length >0){
	db.donations.insertMany(donations)
	print("migration complete!! "+donations.length+" documents migrated.")
	db.migration_info.updateOne(
	   {_id: "mig-donation-info"},
	   { $setOnInsert: { _id : "mig-donation-info", last_migration : new ISODate(),count:donations.length} },
	   { upsert: true }
	)
}else{
	print("nothing to migrate!!")
}

function convertDonations(sourceData){
	sourceData.type=sourceData.contributionType;
	sourceData.status=sourceData.contributionStatus;
	
	/**
	 * if not guest then store donor details from profile
	 * if guest then convert the variables with new vars
	 */
	if(!sourceData.isGuest){
		var profile=sourceDB.profiles.findOne({_id:sourceData.profile})
		//printjson(profile)
		sourceData.donorName= profile.firstName+' '+profile.lastName
		sourceData.donorEmailAddress=profile.email
		sourceData.donorContactNumber=profile.dialCode+profile.contactNumber
	}else{
		sourceData.donorName= sourceData.guestFullNameOrOrgName
		sourceData.donorEmailAddress=sourceData.guestEmailAddress
		sourceData.donorContactNumber=sourceData.guestContactNumber
	}
	
	/**
	 * previously we were only storing account id, now we are also fetching account name
	 * 
	 */
	if(sourceData.accountId){
		var account=sourceDB.accounts.findOne({"$or":[{_id:sourceData.accountId},{_id:ObjectId(sourceData.accountId)}]})
		//printjson(account)
		var profile=sourceDB.profiles.findOne({_id:account.profile})
		sourceData.accountName=profile.firstName+' '+profile.lastName
	}
	
	/**
	 * previously variable was event 
	 * we are storing event inside eventId
	 */
	if(sourceData.event){
		sourceData.eventId=sourceData.event;
	}
	
	/**
	 * previously variable was paymentDone 
	 * we are storing paymentDone inside isPaymentNotified
	 */
	if(sourceData.paymentDone){
		sourceData.isPaymentNotified=sourceData.paymentDone;
	}
	
	/**
	 * previously paymentConfirmedBy was auth0 id now 
	 * we are storing profileId and name of profile
	 */
	if(sourceData.paymentConfirmedBy){
		var profile=sourceDB.profiles.findOne({userId:sourceData.paymentConfirmedBy})
		sourceData.paymentConfirmedBy=profile._id
		sourceData.paymentConfirmedByName=profile.firstName+' '+profile.lastName
	}
	
	if(sourceData.contributionStatus == 'CANCELLED'){
		sourceData.cancelReason=sourceData.comment
	}
	if(sourceData.contributionStatus == 'PAY_LATER'){
		sourceData.payLaterReason=sourceData.comment
	}
	if(sourceData.contributionStatus == 'PAYMENT_FAILED'){
		sourceData.paymentFailDetail=sourceData.comment
	}
	
	//printjson(sourceData)
	return sourceData;
}

/**
* MEMBER
*/

print("migrating profile ...")
const sourceProfile =sourceDB.profiles.find();
const mem_mig_info=db.migration_info.findOne({_id:"mig-member-info"});
let profiles=[];
while(sourceProfile.hasNext()){
	let member =sourceProfile.next()
	if(mem_mig_info == null || member.createdOn > mem_mig_info.last_migration){
		profiles.push(convertMembers(member))
	}
}

if(profiles.length >0){
	db.user_profiles.insertMany(profiles)
	print("migration complete!! "+profiles.length+" documents migrated.")
	db.migration_info.updateOne(
	   {_id: "mig-member-info"},
	   { $setOnInsert: { _id : "mig-member-info", last_migration : new ISODate(),count:profiles.length} },
	   { upsert: true }
	)
}else{
	print("nothing to migrate!!")
}

function convertMembers(sourceData){
	if(sourceData.gender && sourceData.gender=='Male'){
		sourceData.gender='M'
		sourceData.title='MR'
	}else if(sourceData.gender && sourceData.gender=='Female'){
		sourceData.gender='F'
		sourceData.title='MS'
	}
	
	sourceData.phoneNumber=sourceData.dialCode+'-'+sourceData.contactNumber
	sourceData.publicProfile=sourceData.displayPublic?true:false;
	sourceData.status=sourceData.profileStatus;
	if(sourceData.address){
		sourceData.addressLine1=sourceData.address;
	}
	return sourceData;
}

/**
* ACCOUNTS
*/
print("migrating accounts ...")

const sourceAccounts =sourceDB.accounts.find();
const acc_mig_info=db.migration_info.findOne({_id:"mig-account-info"});
let accounts=[];
while(sourceAccounts.hasNext()){
	let account=sourceAccounts.next()
	if(acc_mig_info == null || account.createdOn > acc_mig_info.last_migration){
		accounts.push(convertAccounts(account))
	}
}
if(accounts.length >0){
	db.accounts.insertMany(accounts)
	print("migration complete!! "+accounts.length+" documents migrated.")
	db.migration_info.updateOne(
	   {_id: "mig-account-info"},
	   { $setOnInsert: { _id : "mig-account-info", last_migration : new ISODate(),count:accounts.length} },
	   { upsert: true }
	)
}else{
	print("nothing to migrate!!")
}

function convertAccounts(sourceData){
	//sourceData._id=sourceData._id.toString()
	if(sourceData.profile){
		var profile=sourceDB.profiles.findOne({_id:sourceData.profile})
		sourceData.accountName=profile.firstName+' '+profile.lastName
	}
	
	if(sourceData.accountType){
		sourceData.accountType=sourceData.accountType == 'PRI' ? 'DONATION' : 'GENERAL'
	}
	return sourceData;
}	

/**
* account transactions
*/
print("migrating account transactions ...")

const sourceTxns =sourceDB.transactions.find();
const txn_mig_info=db.migration_info.findOne({_id:"mig-txn-info"});
let txns=[];
while(sourceTxns.hasNext()){
	let txn=sourceTxns.next()
	if(txn_mig_info == null || txn.transactionDate > txn_mig_info.last_migration){
		txns.push(convertTxns(txn))
	}
}
if(txns.length >0){
	db.transactions.insertMany(txns)
	print("migration complete!! "+txns.length+" documents migrated.")
	db.migration_info.updateOne(
	   {_id: "mig-txn-info"},
	   { $setOnInsert: { _id : "mig-txn-info", last_migration : new ISODate(),count:txns.length} },
	   { upsert: true }
	)
}else{
	print("nothing to migrate!!")
}

function convertTxns(sourceData){
	//sourceData._id=sourceData._id.toString()
	if(sourceData.contribution){
		sourceData.transactionRefType='DONATION'
		sourceData.transactionRefId=sourceData.contribution.toString()
		let donation = sourceDB.contributions.findOne({_id:sourceData.contribution})
		//print(donation)
		sourceData.creationDate=donation.paymentConfirmedOn
		sourceData.transactionDescription='Donation amount for id '+sourceData.transactionRefId
	}
	if(sourceData.earning){
		sourceData.transactionRefType='NONE'
		sourceData.transactionRefId=sourceData.earning.toString()
	}
	if(sourceData.expense){
		sourceData.transactionRefType='NONE'
		sourceData.transactionRefId=sourceData.expense.toString()
	}
	
	if(sourceData.fromAccount){
		sourceData.fromAccount=sourceData.fromAccount.toString()
	}
	
	if(sourceData.toAcctount){
		sourceData.toAccount=sourceData.toAcctount.toString()
	}
	return sourceData;
}

/**
* Document ref
*/
print("migrating document ref ...")

const sourceDocRefs =sourceDB.attachments.find();
const doc_ref_mig_info=db.migration_info.findOne({_id:"mig-doc_ref-info"});
let doc_refs=[];
while(sourceDocRefs.hasNext()){
	let doc_ref=sourceDocRefs.next()
	if(doc_ref_mig_info == null || doc_ref.createdOn > doc_ref_mig_info.last_migration){
		doc_refs.push(convertDocRefs(doc_ref))
	}
}
if(doc_refs.length >0){
	db.document_references.insertMany(doc_refs)
	print("migration complete!! "+doc_refs.length+" documents migrated.")
	db.migration_info.updateOne(
	   {_id: "mig-doc_ref-info"},
	   { $setOnInsert: { _id : "mig-doc_ref-info", last_migration : new ISODate(),count:doc_refs.length} },
	   { upsert: true }
	)
}else{
	print("nothing to migrate!!")
}

function convertDocRefs(sourceData){
	if(sourceData.contribution){
		sourceData.documentType='DONATION'
		sourceData.documentRefId=sourceData.contribution.toString()
	}
	if(sourceData.event){
		sourceData.documentType='EVENT'
		sourceData.documentRefId=sourceData.event.toString()
	}
	return sourceData;
}
print("End ...")















