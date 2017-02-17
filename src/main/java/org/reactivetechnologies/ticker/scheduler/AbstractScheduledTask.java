/**
 * Copyright 2017 esutdal

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.reactivetechnologies.ticker.scheduler;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.reactivetechnologies.ticker.messaging.Data;
import org.reactivetechnologies.ticker.messaging.base.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.core.IMap;
/**
 * The base class for creating scheduled tasks. This class will be passed a {@linkplain TaskContext} on each run.
 * @author esutdal
 *
 */
public abstract class AbstractScheduledTask implements ScheduledTask, ScheduledRunnable {

	private static final Logger log = LoggerFactory.getLogger(AbstractScheduledTask.class);
	/**
	 * TODO: do we need an Actor like pattern in a scheduler??
	 */
	protected final LinkedList<ScheduledTask> spawnedTasks = new LinkedList<>();
	private volatile TaskContext key;
	private ScheduledFuture<?> future;
	TaskSchedulerImpl scheduler;
	DelegatingCronTrigger trigger;
	
	protected Publisher publisher;
	
	final void setTaskKey(TaskContext key) {
		this.key = key;
	}
	/**
	 * 
	 * @param cancellable
	 */
	final void setCancellable(ScheduledFuture<?> cancellable) {
		this.future = cancellable;
	}
	public boolean isCancelled()
	{
		Assert.notNull(future, "Not scheduled yet");
		return future.isCancelled();
	}
	public boolean cancel()
	{
		Assert.notNull(future, "Not scheduled yet");
		if(!future.isCancelled())
		{
			
			boolean b = future.cancel(false);
			destroy();
			return b;
		}
		return false;
		
	}
	protected boolean cancelSpawned()
	{
		boolean done = false;
		if(!spawnedTasks.isEmpty())
		{
			for(ScheduledTask kr : spawnedTasks)
			{
				done &= kr.cancel();
			}
		}
		return done;
	}
	
	/**
	 * This method is invoked after every successful invocation of {@link #run(TaskContext)}. This method can be 
	 * used to schedule a new task. The scheduled task will be maintained as a child task of this instance.
	 * @param context
	 * @return
	 */
	protected SpawnedScheduledTask spawnTask(TaskContext context)
	{
		return null;
	}
	/**
	 * This method will be invoked after a task is being cancelled.
	 */
	protected void destroy()
	{
		log.info("AbstractScheduledTask.destroy()");
	}
		
	private void runTask()
	{
		key = run(key);
		if(key.isEmitData())
			doEmit(key.getDataSet());
		SpawnedScheduledTask spawnedTask = spawnTask(key);
		if (spawnedTask != null) 
		{
			scheduler.scheduleAt(spawnedTask, spawnedTask.executeAfter().toDate());
			spawnedTasks.add(spawnedTask);
			log.info("New child task spawned " + spawnedTask);
		}
	}
	protected void doEmit(Iterator<? extends Data> iterator) {
		for(;iterator.hasNext();)
		{
			Data d = iterator.next();
			publisher.offer(d);
			iterator.remove();
		}
		log.info("Emitted dataset for reactive processing..");
	}
	private void run0()
	{
		try 
		{
			runTask();
		}
		catch(Exception e)
		{
			log.error("Scheduler execution exception logged", e);
		}
		finally {
			releaseLock();
		}
	}
	
	@Override
	public void run() {
		try 
		{
			if (acquireLock()) {
				markLocked();
				run0(); 
			}
			else
				log.warn("Did not acquire this run mutex");
		} 
		catch (HazelcastInstanceNotActiveException e) {
			log.error("Hazelcast unavailable. "
					+ (isInLockingState ? "Scheduler ran but is left in a locked state now" : "Scheduler was not run!"));
			log.debug("", e);
			if(isInLockingState)
			{
				//should be handled by Hazelcast.
			}
		}
		catch (Exception e) {
			log.error("Internal Error!", e);
		}
	}
	private volatile boolean isInLockingState;
	private void markLocked() {
		setInLockingState(true);
	}
	static final byte[] VALUE = ".".getBytes(StandardCharsets.UTF_8);
	static final Integer KEY = 1;
	
	/**
	 * Release cluster lock.
	 */
	private void releaseLock() {
		IMap<String, byte[]> map = scheduler.getHazelcastOps().getMap(getClass().getName());
		map.remove(timestampKey);
		markUnlocked();
	}
	private void markUnlocked() {
		setInLockingState(false);
	}
	/**
	 * The lowest denomination of {@linkplain TimeUnit} till which uniqueness of job execution is guaranteed.
	 * Can be HOUR or MINUTE or SECOND. Can be overridden by subclasses to provide a more coarse grained unit.
	 * @return
	 */
	protected abstract TimeUnit scheduleTimeunit();
	private String getTimestampKey()
	{
		Clock clock = scheduler.getClusterClock();
		//clock.setTimestamp(trigger.getNextExecutionTime().getTime()+scheduler.getClockOffset());
		clock.setTimestamp(trigger.getNextExecutionTime().getTime());
		return clock.toTimestampString(scheduleTimeunit());
	}
	private volatile String timestampKey;
	/**
	 * Acquire cluster lock.
	 * @return
	 */
	private boolean acquireLock() 
	{
		timestampKey = getTimestampKey();
		log.debug("timestampKey- "+timestampKey);
		IMap<String, byte[]> map = scheduler.getHazelcastOps().getMap(getClass().getName());
		map.lock(timestampKey);
		try {
			return map.putIfAbsent(timestampKey, VALUE) == null;
		} finally {
			map.unlock(timestampKey);
		}
	}
	/**
	 * Whether this instance has an acquired lock.
	 * @return
	 */
	public boolean isInLockingState() {
		return isInLockingState;
	}
	private void setInLockingState(boolean isInLockingState) {
		this.isInLockingState = isInLockingState;
	}
}
