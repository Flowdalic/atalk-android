/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.geolocation;

import net.java.sip.communicator.impl.protocol.jabber.OperationSetPersistentPresenceJabberImpl;
import net.java.sip.communicator.service.protocol.OperationSetPresence;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.geoloc.packet.GeoLocation;

/**
 * This class represents a Jabber presence message including a Geolocation Extension.
 *
 * @author Guillaume Schreiner
 * @author Eng Chong Meng
 */
public class GeolocationPresence
{

	/**
	 * the presence message to send via a XMPPConnection
	 */
	private Presence prez = null;

	/**
	 *
	 * @param persistentPresence
	 *        OperationSetPresence
	 */
	public GeolocationPresence(OperationSetPresence persistentPresence)
	{
		this.prez = new Presence(Presence.Type.available);

		// set the custom status message
		this.prez.setStatus(persistentPresence.getCurrentStatusMessage());

		// set the presence mode (available, NA, free for chat)
		this.prez.setMode(OperationSetPersistentPresenceJabberImpl
			.presenceStatusToJabberMode(persistentPresence.getPresenceStatus()));
	}

	/**
	 * Set the Geolocation extension packet.
	 *
	 * @param ext
	 *        the <tt>GeolocationPacketExtension</tt> to set
	 */
	public void setGeolocationExtention(GeoLocation ext)
	{
		this.prez.addExtension(ext);
	}

	/**
	 * Get the Geolocation presence message.
	 *
	 * @return the Geolocation presence message.
	 */
	public Presence getGeolocPresence()
	{
		return this.prez;
	}

}
