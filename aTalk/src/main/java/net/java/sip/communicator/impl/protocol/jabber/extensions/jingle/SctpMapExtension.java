/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * SctpMap extension in transport packet extension. Defined by XEP-0343: Signaling WebRTC
 * datachannels in Jingle.
 * 
 * @author lishunyang
 * @author Eng Chong Meng
 * 
 */
public class SctpMapExtension implements ExtensionElement
{
	/**
	 * The name of the "sctpmap" element.
	 */
	public static final String ELEMENT_NAME = "sctpmap";

	/**
	 * The namespace for the "sctpmap" element.
	 */
	public static final String NAMESPACE = "urn:xmpp:jingle:transports:dtls-sctp:1";

	/**
	 * Port number of "sctpmap" element.
	 */
	public static final String PORT_ATTR_NAME = "number";

	/**
	 * Protocol name of "sctpmap" element.
	 */
	public static final String PROTOCOL_ATTR_NAME = "protocol";

	/**
	 * Number of streams of "sctpmap" element.
	 */
	public static final String STREAMS_ATTR_NAME = "streams";

	/**
	 * Value of "port".
	 */
	private int port = -1;

	/**
	 * Value of "protocol".
	 * 
	 * @See SctpMapExtension.Protocol
	 */
	private String protocol = "";

	/**
	 * Number of "streams".
	 */
	private int streams = -1;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getElementName()
	{
		return ELEMENT_NAME;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getNamespace()
	{
		return NAMESPACE;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public XmlStringBuilder toXML()
	{
		XmlStringBuilder xml = new XmlStringBuilder();
		xml.prelude(getElementName(), getNamespace());

		xml.optIntAttribute(PORT_ATTR_NAME, port);
		xml.optAttribute(PROTOCOL_ATTR_NAME, protocol);
		xml.optIntAttribute(STREAMS_ATTR_NAME, streams);

		xml.append("/>");
		return xml;
	}

	public void setPort(int port)
	{
		this.port = port;
	}

	public int getPort()
	{
		return port;
	}

	public void setProtocol(String protocol)
	{
		this.protocol = protocol;
	}

	public void setProtocol(Protocol protocol)
	{
		this.protocol = protocol.toString();
	}

	public String getProtocol()
	{
		return protocol;
	}

	public void setStreams(int streams)
	{
		this.streams = streams;
	}

	public int getStreams()
	{
		return streams;
	}

	/**
	 * Protocol enumeration of <tt>SctpMapExtension</tt>. Currently it only contains WEBRTC_CHANNEL.
	 * 
	 * @author lishunyang
	 * 
	 */
	public static enum Protocol {
		WEBRTC_CHANNEL("webrtc-datachannel");

		private String name;

		private Protocol(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
