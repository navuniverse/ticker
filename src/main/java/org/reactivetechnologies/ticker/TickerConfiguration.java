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
package org.reactivetechnologies.ticker;

import org.reactivetechnologies.ticker.datagrid.HazelcastConfiguration;
import org.reactivetechnologies.ticker.messaging.MessagingConfiguration;
import org.reactivetechnologies.ticker.mqtt.MqttConfiguration;
import org.reactivetechnologies.ticker.rest.RestConfiguration;
import org.reactivetechnologies.ticker.scheduler.SchedulerConfiguration;
import org.reactivetechnologies.ticker.utils.ApplicationContextWrapper;
import org.reactivetechnologies.ticker.utils.JarFileSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({HazelcastConfiguration.class, SchedulerConfiguration.class, MessagingConfiguration.class, RestConfiguration.class, MqttConfiguration.class})
public class TickerConfiguration {
	
	@Bean
	ApplicationContextWrapper ctxHelper()
	{
		return new ApplicationContextWrapper();
	}
	
	@Bean
	JarFileSupport jarSupport()
	{
		return new JarFileSupport();
	}
}
