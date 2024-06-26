package ngo.nabarun.app.businesslogic.businessobjects;

import java.util.Date;
import java.util.List;

import lombok.Data;
import ngo.nabarun.app.common.enums.WorkflowStatus;
import ngo.nabarun.app.common.enums.WorkflowType;

@Data
public class RequestDetail {
	private String id;
	private String name;
	private WorkflowType type;
	private WorkflowStatus status;
	private String description;
	private Date createdOn;
	private Date resolvedOn;
	private UserDetail requester;
	private boolean delegated;
	private UserDetail delegatedRequester;
	private List<AdditionalField> additionalFields;
	//private List<WorkflowStepDetail> workflowSteps;
	private String remarks;


	
}
