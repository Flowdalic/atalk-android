/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.carbon;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;

/**
 * This class implements message carbons extensions added to message stanzas.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class CarbonPacketExtension extends AbstractPacketExtension
{

	/**
	 * The namespace for the XML element.
	 */
	public static final String NAMESPACE = "urn:xmpp:carbons:2";

	/**
	 * The name of the "received" XML element.
	 */
	public static final String RECEIVED_ELEMENT_NAME = "received";

	/**
	 * The name of the "sent" XML element.
	 */
	public static final String SENT_ELEMENT_NAME = "sent";

	/**
	 * The name of the "private" XML element.
	 */
	public static final String PRIVATE_ELEMENT_NAME = "private";

	/**
	 * Constructs new <tt>CarbonPacketExtension</tt> instance.
	 * 
	 * @param elementName
	 *        the name of the XML element.
	 */
	public CarbonPacketExtension(String elementName)
	{
		super(elementName, NAMESPACE);
	}

	/**
	 * Parses sent and received XML elements.
	 */
	public static class Provider extends ExtensionElementProvider<CarbonPacketExtension>
	{

		/**
		 * The name of the elements to be parsed.
		 */
		private String elementName;

		/**
		 * Constructs new <tt>Provider</tt> instance.
		 * 
		 * @param elementName
		 *        the name of the elements to be parsed
		 */
		public Provider(String elementName)
		{
			this.elementName = elementName;
		}

		/**
		 * Creates a <tt>CarbonPacketExtension</tt> by parsing an XML document.
		 * 
		 * @param parser
		 *        the parser to use.
		 * @return the created <tt>CarbonPacketExtension</tt>.
		 * @throws Exception
		 */
		@Override
		public CarbonPacketExtension parse(XmlPullParser parser, int depth)
				throws Exception
		{
			CarbonPacketExtension packetExtension = new CarbonPacketExtension(elementName);

			// now parse the sub elements
			boolean done = false;
			String elementName;
			CarbonPacketExtension extension = null;
			while (!done) {
				switch (parser.next()) {
					case XmlPullParser.START_TAG: {
						elementName = parser.getName();
						if (ForwardedPacketExtension.ELEMENT_NAME.equals(elementName)) {
							extension = super.parse(parser);
							if (extension != null) {
								packetExtension.addChildExtension(extension);
							}
						}
						break;
					}
					case XmlPullParser.END_TAG: {
						elementName = parser.getName();
						if (this.elementName.equals(elementName)) {
							done = true;
						}
						break;
					}
				}
			}
			return packetExtension;
		}
	}

	/**
	 * This class implements the private carbon extension.
	 */
	public static class PrivateExtension extends AbstractPacketExtension
	{
		public PrivateExtension()
		{
			super(PRIVATE_ELEMENT_NAME, NAMESPACE);
		}

	}
}
