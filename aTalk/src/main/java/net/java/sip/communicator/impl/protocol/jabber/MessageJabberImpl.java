/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AbstractMessage;

/**
 * A simple implementation of the <tt>Message</tt> interface. Right now the message only supports
 * test contents and no binary data.
 *
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class MessageJabberImpl extends AbstractMessage
{
	/**
	 * Creates an instance of this Message with the specified parameters.
	 *
	 * @param content
	 *        the text content of the message.
	 * @param encType
	 *        an int value indicating the content type of the <tt>content</tt> String.
	 * @param subject
	 *        the subject of the message or null for empty.
	 */
	public MessageJabberImpl(String content, int encType, String subject)
	{
		super(content, encType, subject);
	}

	/**
	 * Creates an instance of this Message with the specified parameters.
	 *
	 * @param content
	 *        the text content of the message.
	 * @param encType
	 *        an int value indicating the content type of the <tt>content</tt> String.
	 * @param subject
	 *        the subject of the message or null for empty.
	 * @param
	 */
	public MessageJabberImpl(String content, int encType, String subject, String messageUID)
	{
		super(content, encType, subject, messageUID);
	}
}
