package net.spy.memcached.protocol.binary;

import net.spy.memcached.ops.DeleteOperation;
import net.spy.memcached.ops.OperationCallback;

class DeleteOperationImpl extends SingleKeyOperationImpl implements
		DeleteOperation {

	private static final int CMD=0x04;

	private final long cas;

	public DeleteOperationImpl(String k, OperationCallback cb) {
		this(k, 0, cb);
	}

	public DeleteOperationImpl(String k, long c, OperationCallback cb) {
		super(CMD, generateOpaque(), k, cb);
		cas=c;
	}

	@Override
	public void initialize() {
		prepareBuffer(key, cas, EMPTY_BYTES);
	}

}
