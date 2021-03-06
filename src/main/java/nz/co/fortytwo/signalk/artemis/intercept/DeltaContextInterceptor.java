package nz.co.fortytwo.signalk.artemis.intercept;

import static nz.co.fortytwo.signalk.artemis.util.Config.INCOMING_RAW;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.CONFIG;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.CONTEXT;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mjson.Json;
import nz.co.fortytwo.signalk.artemis.util.Config;
import nz.co.fortytwo.signalk.artemis.util.Util;

/*
*
* Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
* Web: www.42.co.nz
* Email: robert@42.co.nz
* Author: R T Huitema
*
* This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
* WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/

/**
 * Checks for a CONTEXT attribute, adds the default '/vessels/self' if none.
 * 
 * @author robert
 * 
 */

public class DeltaContextInterceptor extends BaseInterceptor implements Interceptor {

	private static Logger logger = LogManager.getLogger(DeltaContextInterceptor.class);

	/**
	 * Checks for a CONTEXT attribute, adds the default '/vessels/self' if none.
	 * Does nothing if json is not a delta, and returns the original message
	 * 
	 * @param node
	 * @return
	 */

	@Override
	public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {

		if (packet instanceof SessionSendMessage) {
			
			ICoreMessage message = ((SessionSendMessage) packet).getMessage();
			
			if(ignoreMessage(INCOMING_RAW, Config.AMQ_CONTENT_TYPE_JSON_DELTA, message))return true;
			
			Json node = Util.readBodyBuffer(message);

			if (logger.isDebugEnabled())
				logger.debug("Delta msg: {}", node.toString());

			try {
				if (logger.isDebugEnabled())
					logger.debug("Converting source in delta: {}", node.toString());
				if (node.has(CONFIG) && node.has(CONTEXT)) {
					node.delAt(CONTEXT);
					message.getBodyBuffer().clear();
					message.getBodyBuffer().writeString(node.toString());
				}
				if (!node.has(CONTEXT) && !node.has(CONFIG)) {
					node.set(CONTEXT, "/vessels.self");
					message.getBodyBuffer().clear();
					message.getBodyBuffer().writeString(node.toString());
				}
				node.clear(true);
				return true;
			} catch (Exception e) {
				logger.error(e, e);
				throw new ActiveMQException(ActiveMQExceptionType.INTERNAL_ERROR, e.getMessage(), e);
			}

		}
		return true;

	}

	

}
