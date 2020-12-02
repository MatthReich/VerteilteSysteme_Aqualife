package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public final class RegisterResponse implements Serializable {
	private final int DEFAULT_LEASE = 10000; // 10 seconds
	private final String id;
	private final int lease;

	public RegisterResponse(String id, int lease) {
		this.id = id;
		this.lease = lease;
	}

	public RegisterResponse(String id) {
		this.id = id;
		this.lease = DEFAULT_LEASE;
	}

	public String getId() {
		return id;
	}

	public int getLease() {
		return lease;
	}
}
