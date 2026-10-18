/**
 * JalDrishti Adapter — Notification Channels.
 *
 * <p>Implements the {@code ChannelAdapter} interface (send, receiveAck, deliveryStatus)
 * for each channel:
 * <ul>
 *   <li>Web phone simulator (primary demo channel)</li>
 *   <li>Telegram bot (secondary demo channel)</li>
 *   <li>SMS (optional, via provider sandbox)</li>
 *   <li>Voice call (escalation step 1)</li>
 * </ul>
 *
 * <p>Reviewed message templates are in {@code resources/templates/} as a safety net
 * when Gemini localisation fails or times out.
 */
package org.jaldrishti.adapter.channels;
