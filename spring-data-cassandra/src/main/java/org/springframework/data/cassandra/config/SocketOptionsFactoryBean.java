/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.cassandra.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import com.datastax.driver.core.SocketOptions;

/**
 * Socket Options Factory Bean.
 *
 * @author Matthew T. Adams
 * @author David Webb
 */
public class SocketOptionsFactoryBean implements FactoryBean<SocketOptions>, InitializingBean, DisposableBean {

	private Integer connectTimeoutMillis;
	private Boolean keepAlive;
	private Integer readTimeoutMillis;
	private Boolean reuseAddress;
	private Integer soLinger;
	private Boolean tcpNoDelay;
	private Integer receiveBufferSize;
	private Integer sendBufferSize;

	private SocketOptions socketOptions;

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	@Override
	public SocketOptions getObject() throws Exception {
		return socketOptions;
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 */
	@Override
	public Class<?> getObjectType() {
		return SocketOptions.class;
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.DisposableBean#destroy()
	 */
	@Override
	public void destroy() throws Exception {

		connectTimeoutMillis = null;
		keepAlive = null;
		readTimeoutMillis = null;
		reuseAddress = null;
		soLinger = null;
		tcpNoDelay = null;
		receiveBufferSize = null;
		sendBufferSize = null;
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	@Override
	public void afterPropertiesSet() throws Exception {

		socketOptions = new SocketOptions();

		if (connectTimeoutMillis != null) {
			socketOptions.setConnectTimeoutMillis(connectTimeoutMillis);
		}

		if (keepAlive != null) {
			socketOptions.setKeepAlive(keepAlive);
		}

		if (readTimeoutMillis != null) {
			socketOptions.setReadTimeoutMillis(readTimeoutMillis);
		}

		if (reuseAddress != null) {
			socketOptions.setReuseAddress(reuseAddress);
		}

		if (soLinger != null) {
			socketOptions.setSoLinger(soLinger);
		}

		if (tcpNoDelay != null) {
			socketOptions.setTcpNoDelay(tcpNoDelay);
		}

		if (receiveBufferSize != null) {
			socketOptions.setReceiveBufferSize(receiveBufferSize);
		}

		if (sendBufferSize != null) {
			socketOptions.setSendBufferSize(sendBufferSize);
		}

	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.FactoryBean#isSingleton()
	 */
	@Override
	public boolean isSingleton() {
		return true;
	}

	public Boolean getKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(Boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	public Boolean getReuseAddress() {
		return reuseAddress;
	}

	public void setReuseAddress(Boolean reuseAddress) {
		this.reuseAddress = reuseAddress;
	}

	public Integer getSoLinger() {
		return soLinger;
	}

	public void setSoLinger(Integer soLinger) {
		this.soLinger = soLinger;
	}

	public Boolean getTcpNoDelay() {
		return tcpNoDelay;
	}

	public void setTcpNoDelay(Boolean tcpNoDelay) {
		this.tcpNoDelay = tcpNoDelay;
	}

	public Integer getReceiveBufferSize() {
		return receiveBufferSize;
	}

	public void setReceiveBufferSize(Integer receiveBufferSize) {
		this.receiveBufferSize = receiveBufferSize;
	}

	public Integer getSendBufferSize() {
		return sendBufferSize;
	}

	public void setSendBufferSize(Integer sendBufferSize) {
		this.sendBufferSize = sendBufferSize;
	}

	/**
	 * @return Returns the connectTimeoutMillis.
	 */
	public Integer getConnectTimeoutMillis() {
		return connectTimeoutMillis;
	}

	/**
	 * @param connectTimeoutMillis The connectTimeoutMillis to set.
	 */
	public void setConnectTimeoutMillis(Integer connectTimeoutMillis) {
		this.connectTimeoutMillis = connectTimeoutMillis;
	}

	/**
	 * @return Returns the readTimeoutMillis.
	 */
	public Integer getReadTimeoutMillis() {
		return readTimeoutMillis;
	}

	/**
	 * @param readTimeoutMillis The readTimeoutMillis to set.
	 */
	public void setReadTimeoutMillis(Integer readTimeoutMillis) {
		this.readTimeoutMillis = readTimeoutMillis;
	}

}
