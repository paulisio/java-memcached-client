package net.spy.memcached.internal;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import net.spy.memcached.OperationTimeoutException;
import net.spy.memcached.compat.SpyObject;
import net.spy.memcached.ops.OperationStatus;
import net.spy.memcached.protocol.couch.HttpOperation;
import net.spy.memcached.protocol.couch.ViewResponseWithDocs;
import net.spy.memcached.protocol.couch.RowWithDocs;

public class ViewFuture extends SpyObject implements
		Future<ViewResponseWithDocs> {
	private final AtomicReference<ViewResponseWithDocs> viewRef;
	private final AtomicReference<BulkFuture<Map<String, Object>>> multigetRef;
	private OperationStatus status;
	private final CountDownLatch latch;

	private final long timeout;
	private HttpOperation op;

	public ViewFuture(CountDownLatch latch, long timeout) {
		super();
		this.viewRef = new AtomicReference<ViewResponseWithDocs>(null);
		this.multigetRef = new AtomicReference<BulkFuture<Map<String, Object>>>(null);
		this.latch = latch;
		this.timeout = timeout;
		this.status = null;
	}

	public boolean cancel(boolean c) {
		op.cancel();
		return true;
	}

	@Override
	public ViewResponseWithDocs get() throws InterruptedException,
			ExecutionException {
		try {
			return get(timeout, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			status = new OperationStatus(false, "Timed out");
			throw new RuntimeException("Timed out waiting for operation", e);
		}
	}

	@Override
	public ViewResponseWithDocs get(long duration, TimeUnit units)
			throws InterruptedException, ExecutionException, TimeoutException {

		if (!latch.await(duration, units)) {
			if (op != null) {
				op.timeOut();
			}
			status = new OperationStatus(false, "Timed out");
			throw new TimeoutException("Timed out waiting for operation");
		}

		if (op != null && op.hasErrored()) {
			status = new OperationStatus(false, op.getException().getMessage());
			throw new ExecutionException(op.getException());
		}

		if (op.isCancelled()) {
			status = new OperationStatus(false, "Operation Cancelled");
			throw new ExecutionException(new RuntimeException("Cancelled"));
		}

		if (op != null && op.isTimedOut()) {
			status = new OperationStatus(false, "Timed out");
			throw new ExecutionException(new OperationTimeoutException(
					"Operation timed out."));
		}

		Map<String, Object> docMap = multigetRef.get().get();
		final ViewResponseWithDocs view = (ViewResponseWithDocs) viewRef.get();
		Collection<RowWithDocs> rows = new LinkedList<RowWithDocs>();
		Iterator<RowWithDocs> itr = view.iterator();

		while (itr.hasNext()) {
			RowWithDocs r = itr.next();
			rows.add(new RowWithDocs(r.getId(), r.getKey(), r.getValue(),
					docMap.get(r.getId())));
		}

		return new ViewResponseWithDocs(rows, view.getErrors());
	}

	public OperationStatus getStatus() {
		if (status == null) {
			try {
				get();
			} catch (InterruptedException e) {
				status = new OperationStatus(false, "Interrupted");
				Thread.currentThread().isInterrupted();
			} catch (ExecutionException e) {
				getLogger().warn("Error getting status of operation", e);
			}
		}
		return status;
	}

	@Override
	public boolean isCancelled() {
		assert op != null : "No operation";
		return op.isCancelled();
	}

	@Override
	public boolean isDone() {
		assert op != null : "No operation";
		return latch.getCount() == 0 || op.isCancelled() || op.hasErrored();
	}

	public void setOperation(HttpOperation to) {
		this.op = to;
	}

	public void set(ViewResponseWithDocs viewResponse,
			BulkFuture<Map<String, Object>> op, OperationStatus s) {
		viewRef.set(viewResponse);
		multigetRef.set(op);
		status = s;
	}
}
