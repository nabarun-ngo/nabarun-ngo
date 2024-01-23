package ngo.nabarun.app.api.response;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import ngo.nabarun.app.businesslogic.exception.BusinessException;

@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {

	private static final String DEFAULT_ERROR_MESSAGE = "Something went wrong.";

	@JsonProperty("info")
	private final String info = "Error";

	@JsonProperty("timestamp")
	private Date timestamp = new Date();

	@JsonProperty("status")
	private int status;

	@JsonProperty("messages")
	private List<String> messages = new ArrayList<String>();

	@JsonProperty("errorCause")
	private String errorCause;

	@JsonProperty("details")
	private List<String> details = new ArrayList<String>();

	@JsonProperty("stackTrace")
	private String[] stackTrace;

	public ErrorResponse(Exception e) {
		messages.add(e instanceof BusinessException ? e.getMessage() : DEFAULT_ERROR_MESSAGE);
		details.add("Message : " + e.getMessage());
		if (e.getCause() != null) {
			details.add("Cause : " + e.getCause().getMessage());
		}
		details.add("Exception : " + e.getClass().getSimpleName());
		errorCause = e.getCause() != null ? e.getCause().getMessage() : null;
		// this.stackTrace=ExceptionUtils.getStackFrames(e);

	}

	public ErrorResponse message(String... messages) {
		this.messages.clear();
		for (String message : messages) {
			this.messages.add(message);
		}
		return this;
	}

	public ResponseEntity<ErrorResponse> get(HttpStatus status) {
		this.status = status.value();
		return new ResponseEntity<>(this, status);
	}

}