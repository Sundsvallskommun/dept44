package se.sundsvall.dept44.test.extension.supportfiles;

import java.time.OffsetDateTime;

public class TestObject {

	private String key;
	private String value;
	private OffsetDateTime offsetDateTime;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public OffsetDateTime getOffsetDateTime() {
		return offsetDateTime;
	}

	public void setOffsetDateTime(OffsetDateTime offsetDateTime) {
		this.offsetDateTime = offsetDateTime;
	}
}
