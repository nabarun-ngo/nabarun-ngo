package ngo.nabarun.app.infra.serviceimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.querydsl.core.BooleanBuilder;

import ngo.nabarun.app.common.util.CommonUtils;
import ngo.nabarun.app.infra.core.entity.QWorkListEntity;
import ngo.nabarun.app.infra.core.entity.QWorkflowEntity;
import ngo.nabarun.app.infra.core.entity.WorkListEntity;
import ngo.nabarun.app.infra.core.entity.WorkflowEntity;
import ngo.nabarun.app.infra.core.repo.WorkListRepository;
import ngo.nabarun.app.infra.core.repo.WorkflowRepository;
import ngo.nabarun.app.infra.dto.FieldDTO;
import ngo.nabarun.app.infra.dto.UserDTO;
import ngo.nabarun.app.infra.dto.RequestDTO;
import ngo.nabarun.app.infra.dto.RequestDTO.RequestDTOFilter;
import ngo.nabarun.app.infra.dto.WorkDTO;
import ngo.nabarun.app.infra.dto.WorkDTO.WorkListDTOFilter;
import ngo.nabarun.app.infra.misc.InfraDTOHelper;
import ngo.nabarun.app.infra.misc.InfraFieldHelper;
import ngo.nabarun.app.infra.misc.WhereClause;
import ngo.nabarun.app.infra.service.IWorkflowInfraService;

@Service
public class WorkflowInfraServiceImpl extends BaseServiceImpl implements IWorkflowInfraService {
	
	@Autowired
	private WorkflowRepository workflowRepository;
	
	@Autowired
	private WorkListRepository worklistRepository;

	@Override
	public RequestDTO createWorkflow(RequestDTO workflowDTO) {
		WorkflowEntity workflow= new WorkflowEntity();
		workflow.setId(workflowDTO.getId());
		workflow.setCreatedBy(null);
		workflow.setCreatedOn(CommonUtils.getSystemDate());
		workflow.setDelegated(workflowDTO.isDelegated());
		if(workflowDTO.getRequester() != null) {
			UserDTO req=workflowDTO.getRequester();
			workflow.setProfileId(req.getProfileId());
			workflow.setProfileEmail(req.getEmail());
			String name = req.getName() == null ? String.join(" ",req.getFirstName(),req.getLastName()) : req.getName();
			workflow.setProfileName(name);
		}
		if(workflowDTO.getDelegatedRequester() != null) {
			workflow.setDelegateProfileId(workflowDTO.getDelegatedRequester().getProfileId());
			workflow.setDelegateProfileEmail(workflowDTO.getDelegatedRequester().getEmail());
			workflow.setDelegateProfileName(workflowDTO.getDelegatedRequester().getName());
		}
		workflow.setDescription(workflowDTO.getDescription());
		workflow.setName(workflowDTO.getWorkflowName());
		workflow.setRemarks(workflowDTO.getRemarks());
		workflow.setStatus(workflowDTO.getStatus().name());
		workflow.setType(workflowDTO.getType().name());
		workflow=workflowRepository.save(workflow);
		if (workflowDTO.getAdditionalFields() != null) {
			for (FieldDTO addfield : workflowDTO.getAdditionalFields()) {
				addfield.setFieldSource(workflowDTO.getId());
				addOrUpdateCustomField(addfield);
			}
		}
		return InfraDTOHelper.convertToWorkflowDTO(workflow,propertyHelper.getAppSecret());
	}
	
	@Override
	public Page<RequestDTO> getWorkflows(Integer page, Integer size, RequestDTOFilter filter) {
		Page<WorkflowEntity> workflowPage = null;
		Sort sort = Sort.by(Sort.Direction.DESC, "createdOn");
		if (filter != null) {

			/*
			 * Query building and filter logic
			 */
			QWorkflowEntity qWorkflow = QWorkflowEntity.workflowEntity;
			BooleanBuilder query = WhereClause.builder()
					.optionalAnd(filter.getWorkflowId() != null, () -> qWorkflow.id.eq(filter.getWorkflowId()))
					.optionalAnd(filter.getRequesterId() != null, () -> qWorkflow.profileId.eq(filter.getRequesterId()))
					.optionalAnd(filter.getDelegatedRequesterId() != null, () -> qWorkflow.delegateProfileId.eq(filter.getDelegatedRequesterId()))
					.optionalAnd(filter.getWorkflowStatus() != null,
							() -> qWorkflow.status.in(filter.getWorkflowStatus().stream().map(m -> m.name()).toList()))
					.optionalAnd(filter.getWorkflowType() != null,
							() -> qWorkflow.type.in(filter.getWorkflowType().stream().map(m -> m.name()).toList()))
					.optionalAnd(filter.getFromDate() != null && filter.getToDate() != null,
							() -> qWorkflow.createdOn.between(filter.getFromDate(), filter.getToDate()))
					.build();

			if (page == null || size == null) {
				List<WorkflowEntity> result = new ArrayList<>();
				workflowRepository.findAll(query, sort).iterator().forEachRemaining(result::add);
				workflowPage = new PageImpl<>(result);
			} else {
				workflowPage = workflowRepository.findAll(query, PageRequest.of(page, size, sort));
			}
		} else if (page != null && size != null) {
			workflowPage = workflowRepository.findAll(PageRequest.of(page, size, sort));
		} else {
			workflowPage = new PageImpl<>(workflowRepository.findAll(sort));
		}
		return workflowPage.map(m->InfraDTOHelper.convertToWorkflowDTO(m,propertyHelper.getAppSecret()));
	}

	@Override
	public RequestDTO getWorkflow(String id) {
		WorkflowEntity workflow=workflowRepository.findById(id).orElseThrow();
		return InfraDTOHelper.convertToWorkflowDTO(workflow, propertyHelper.getAppSecret());
	}

	@Override
	public RequestDTO updateWorkflow(String id, RequestDTO workflowDTO) {
		WorkflowEntity workflowOri= workflowRepository.findById(id).orElseThrow();
		WorkflowEntity workflow= new WorkflowEntity();

		if(workflowDTO.getRequester() != null) {
			UserDTO req=workflowDTO.getRequester();
			workflow.setProfileId(req.getProfileId());
			workflow.setProfileEmail(req.getEmail());
			String name = req.getName() == null ? String.join(" ",req.getFirstName(),req.getLastName()) : req.getName();
			workflow.setProfileName(name);
		}
		
		workflow.setRemarks(workflowDTO.getRemarks());
		if(workflowDTO.getStatus() != null) {
			workflow.setStatus(workflowDTO.getStatus().name());
			if(workflow.getStatus() != workflowDTO.getStatus().name()) {
				workflow.setLastStatus(workflow.getStatus());
			}
		}
		workflow.setLastActionCompleted(workflowDTO.isLastActionCompleted());
		
		CommonUtils.copyNonNullProperties(workflow, workflowOri);
		workflowOri.setCustomFields(null);
		workflow=workflowRepository.save(workflowOri);
		if (workflowDTO.getAdditionalFields() != null) {
			for (FieldDTO addfield : workflowDTO.getAdditionalFields()) {
				addfield.setFieldSource(workflowDTO.getId());
				addOrUpdateCustomField(addfield);
			}
		}
		return InfraDTOHelper.convertToWorkflowDTO(workflow,propertyHelper.getAppSecret());
	}
	
	@Override
	public WorkDTO getWorkList(String id) {
		WorkListEntity worklist = worklistRepository.findById(id).orElseThrow();
		return InfraDTOHelper.convertToWorkListDTO(worklist);
	}

	@Override
	public WorkDTO createWorkList(WorkDTO worklistDTO) {
		WorkListEntity worklist= new WorkListEntity();
		worklist.setCreatedOn(CommonUtils.getSystemDate());
		worklist.setCurrentAction(worklistDTO.getDecision()==null ? null : worklistDTO.getDecision().name());
		worklist.setDescription(worklistDTO.getDescription());
		worklist.setGroupWork(worklistDTO.isGroupWork());
		worklist.setId(worklistDTO.getId() == null ?  UUID.randomUUID().toString():worklistDTO.getId());
		worklist.setPendingWithRoleGroups(InfraFieldHelper.stringListToString(worklistDTO.getPendingWithRoleGroups()));
		if(worklistDTO.getPendingWithRoles() != null && !worklistDTO.getPendingWithRoles().isEmpty()) {
			worklist.setPendingWithRoles(InfraFieldHelper.stringListToString(worklistDTO.getPendingWithRoles().stream().map(m->m.name()).collect(Collectors.toList())));
		}
		if(worklistDTO.getPendingWithUsers() != null && !worklistDTO.getPendingWithUsers().isEmpty()) {
			List<String> ids=worklistDTO.getPendingWithUsers().stream().map(m->m.getUserId()).collect(Collectors.toList());
			List<String> names=worklistDTO.getPendingWithUsers().stream().map(m->m.getName()).collect(Collectors.toList());
			worklist.setPendingWithUserId(InfraFieldHelper.stringListToString(ids));
			worklist.setPendingWithUserName(InfraFieldHelper.stringListToString(names));
		}
		
		worklist.setSourceId(worklistDTO.getWorkflowId());
		worklist.setSourceStatus(worklistDTO.getWorkflowStatus()== null ? null : worklistDTO.getWorkflowStatus().name());
		worklist.setSourceType(worklistDTO.getWorkflowType()== null ? null : worklistDTO.getWorkflowType().name());
		worklist.setStepCompleted(worklistDTO.getStepCompleted() == null ?false : worklistDTO.getStepCompleted());
		worklist.setWorkType(worklistDTO.getWorkType()== null ? null : worklistDTO.getWorkType().name());
		worklist.setFinalStep(worklistDTO.isFinalStep());
		worklist.setActionPerformed(worklistDTO.getActionPerformed() == null ? false : worklistDTO.getActionPerformed());
		worklist=worklistRepository.save(worklist);
		return InfraDTOHelper.convertToWorkListDTO(worklist);
	}
	
	@Override
	public WorkDTO updateWorkList(String id,WorkDTO worklistDTO) {
		WorkListEntity worklist = worklistRepository.findById(id).orElseThrow();
		WorkListEntity updatedWorklist = new WorkListEntity();
		updatedWorklist.setActionPerformed(worklistDTO.getActionPerformed() == null ? false : worklistDTO.getActionPerformed());
		updatedWorklist.setDecision(worklistDTO.getDecision()== null ? null : worklistDTO.getDecision().name());
		updatedWorklist.setDecisionMakerId(worklistDTO.getDecisionMaker() == null ? null : worklistDTO.getDecisionMaker().getProfileId());
		updatedWorklist.setDecisionMakerName(worklistDTO.getDecisionMaker() == null ? null : worklistDTO.getDecisionMaker().getName());
		updatedWorklist.setDecisionMakerRoleGroup(worklistDTO.getDecisionMakerRoleGroup());
		updatedWorklist.setRemarks(worklistDTO.getRemarks());
		updatedWorklist.setStepCompleted(worklistDTO.getStepCompleted()== null ? false : worklistDTO.getStepCompleted());
		updatedWorklist.setDecisionDate(worklistDTO.getDecisionDate());
		CommonUtils.copyNonNullProperties(updatedWorklist, worklist);
		worklist = worklistRepository.save(worklist);
		return InfraDTOHelper.convertToWorkListDTO(worklist);
	}

	@Override
	public Page<WorkDTO> getWorkList(Integer page, Integer size, WorkListDTOFilter filter) {
		Page<WorkListEntity> worklistPage = null;
		Sort sort = Sort.by(Sort.Direction.DESC, "createdOn");
		if (filter != null) {

			/*
			 * Query building and filter logic
			 */
			QWorkListEntity qWorklist = QWorkListEntity.workListEntity;
			BooleanBuilder query = WhereClause.builder()
					.optionalAnd(filter.getWorkflowId() != null, () -> qWorklist.id.eq(filter.getWorkflowId()))
					.optionalAnd(filter.getPendingWithUserId() != null, () -> qWorklist.pendingWithUserId.contains(filter.getPendingWithUserId()))
					.optionalAnd(filter.getPendingWithRoles() != null, () -> qWorklist.pendingWithRoles.in(filter.getPendingWithRoles().stream().map(m->m.name()).toList()))
					.optionalAnd(filter.getDecisionMakerProfileId() != null, () -> qWorklist.decisionMakerId.eq(filter.getDecisionMakerProfileId()))
					.and(qWorklist.stepCompleted.eq(filter.isStepCompleted()))

					.build();

			if (page == null || size == null) {
				List<WorkListEntity> result = new ArrayList<>();
				worklistRepository.findAll(query, sort).iterator().forEachRemaining(result::add);
				worklistPage = new PageImpl<>(result);
			} else {
				worklistPage = worklistRepository.findAll(query, PageRequest.of(page, size, sort));
			}
		} else if (page != null && size != null) {
			worklistPage = worklistRepository.findAll(PageRequest.of(page, size, sort));
		} else {
			worklistPage = new PageImpl<>(worklistRepository.findAll(sort));
		}
		return worklistPage.map(m->InfraDTOHelper.convertToWorkListDTO(m));
	}

}
