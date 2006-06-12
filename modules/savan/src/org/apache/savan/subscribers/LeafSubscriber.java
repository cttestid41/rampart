/*
 * Copyright  1999-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.savan.subscribers;

import java.util.Calendar;
import java.util.Date;

import org.apache.savan.SavanException;
import org.apache.savan.SavanMessageContext;
import org.apache.savan.subscription.ExpirationBean;
import org.apache.savan.util.CommonUtil;


public abstract class LeafSubscriber extends Subscriber {
	
	private Date subscriptionEndingTime = null;
	
	public void renewSubscription (ExpirationBean bean) {
		if (bean.isDuration()) {
			if (subscriptionEndingTime==null) {
				Calendar calendar = Calendar.getInstance();
				CommonUtil.addDurationToCalendar(calendar,bean.getDurationValue());
				subscriptionEndingTime = calendar.getTime();
			} else {
				Calendar expiration = Calendar.getInstance();
				expiration.setTime(subscriptionEndingTime);
				CommonUtil.addDurationToCalendar(expiration,bean.getDurationValue());
				subscriptionEndingTime = expiration.getTime();
			}
		} else
			subscriptionEndingTime = bean.getDateValue();
	}
	
	public Date getSubscriptionEndingTime () {
		return subscriptionEndingTime;
	}
	
	public void setSubscriptionEndingTime () {
		
	}
	
	public void sendNotification(SavanMessageContext notificationMessage) throws SavanException {
		Date date = new Date ();
		
		boolean expired = false;
		if (subscriptionEndingTime!=null && date.after(subscriptionEndingTime))
			expired = true;
		
		if (expired) {
			String message = "Cant notify the listner since the subscription has been expired";
			throw new SavanException (message);
		}
		
		doProtocolSpecificNotification (notificationMessage);
	}
	
	
	
	public void setSubscriptionEndingTime(Date subscriptionEndingTime) {
		this.subscriptionEndingTime = subscriptionEndingTime;
	}

	public abstract void doProtocolSpecificNotification (SavanMessageContext notificationMessage) throws SavanException;
}