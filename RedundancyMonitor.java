package net.floodlightcontroller.chown;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;

import org.openflow.protocol.OFError;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedundancyMonitor implements IOFMessageListener, IFloodlightModule {
	protected IFloodlightProviderService floodlightProvider;
	protected Set macAddresses;
	protected static Logger logger;
	// for dedu work
	public static final int UDP_SERVER_PORT = 1234;
	public static final int UDP_CMD_PORT = 4321;
	public static final String serverIP = "10.0.0.2";

	@Override
	// Need provide ID for OFMessageListener
	public String getName() {
		return "redundancy-monitor";
	}

	// TODO the callback orders ???
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		macAddresses = new ConcurrentSkipListSet<Long>();
		logger = LoggerFactory.getLogger(RedundancyMonitor.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		// we are interested in pktin msg
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
		case ERROR:
			logger.info("received an error {} from switch {}", (OFError) msg,
					sw);
			return Command.CONTINUE;
		}
		logger.error("received an unexpected message {} from switch {}", msg,
				sw);
		return Command.CONTINUE;
	}

	private Command processPacketInMessage(IOFSwitch sw, OFPacketIn msg,
			FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		IPacket commandPkt = null;// udp packet to send
		OFPacketOut po = null; // packet out for this above udp pkt
		String srcMac = HexString.toHexString(eth.getSourceMACAddress());
		
		//logger.info("Packet-In from switch {},eth type {}", srcMac,eth.getEtherType());
		/*
		OFMatch match = new OFMatch();
		match.loadFromPacket(msg.getPacketData(), msg.getInPort());
		logger.info("Packet-In host Port  {}", match.getTransportSource() + ":"+ match.getTransportDestination());
		*/
		// We filter what we need
		if (eth.getEtherType() == Ethernet.TYPE_IPv4) {
			IPv4 ipPkt = (IPv4) eth.getPayload();
			String srcHostIP = IPv4.fromIPv4Address(ipPkt.getSourceAddress());
			if (ipPkt.getProtocol() == IPv4.PROTOCOL_UDP) {
				UDP udpPkt = (UDP) ipPkt.getPayload();
				short srcPort = udpPkt.getSourcePort();
				short destPort = udpPkt.getDestinationPort();
				logger.info("Packet-In host IP {}", srcHostIP);
				logger.info("Packet-In host Port  {}", srcPort + ":"+ destPort);
				if ( destPort!= UDP_SERVER_PORT)
					return Command.CONTINUE;

				Data dataPkt = (Data) udpPkt.getPayload();
				printL7Data(dataPkt);

				// send command to the udp client
				commandPkt = getCommandUDPPkt(srcMac, srcHostIP, srcPort);
				po = getPacketOut(commandPkt, msg.getInPort());
				logger.info("PacketOut:{}", po.toString());
				// and then send to the client..
				try {
					sw.write(po, cntx);
					logger.info("Packet Out of UDP sent !!");
				} catch (IOException e) {
					logger.error("Failure writing PacketOut", e);
				}

			}
		}
		return null;
	}

	private Command processPacketInMessageBad(IOFSwitch sw, OFPacketIn msg,
			FloodlightContext cntx) {
		// i want to see the packet in data,to see if get such as the file name

		OFPacketIn pi = (OFPacketIn) msg;
		// logger.info("PacketIn buffer id :" + pi.getBufferId());

		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());
		// logger.info("Packet in ether type: {}", match.getDataLayerType());
		// logger.info("Destination port num: " +
		// (short)match.getTransportDestination());
		if (match.getNetworkProtocol() == 0x11
				&& match.getTransportDestination() == 1234) {
			// UDP
			byte[] packetData = pi.getPacketData();
			logger.info("PacketIn packet data len: " + packetData.length);
			StringBuffer buff = new StringBuffer();
			for (int i = 0; i < packetData.length; i++) {
				buff.append((char) pi.getPacketData()[i]);
			}
			// String filename = buff.substring(54, buff.length());
			logger.info("UDP data : {}", buff);

		}

		/*
		 * After got the file name from the packet_in, we push the appropriate
		 * fp vector to the SW (TODO:the switches along the path)
		 */
		/*
		 * OFFPUpdate fu = (OFFPUpdate) floodlightProvider.getOFMessageFactory()
		 * .getMessage(OFType.FP_UPDATE); OFActionFPUpdate action = new
		 * OFActionFPUpdate(); action.setVector(0xffffffff); // just for testing
		 * List<OFAction> actions = new ArrayList<OFAction>();
		 * actions.add(action);
		 * 
		 * fu.setBufferId(OFFPUpdate.BUFFER_ID_NONE) .setInPort((short)0)
		 * .setActions(actions)
		 * .setLengthU(OFFPUpdate.MINIMUM_LENGTH+OFActionFPUpdate
		 * .MINIMUM_LENGTH); try { sw.write(fu, cntx); } catch (IOException e) {
		 * logger.error("Failure writing fp update", e); }
		 */
		/*
		 * // get mac Ethernet eth = floodlightProvider.bcStore.get(cntx,
		 * IFloodlightProviderService.CONTEXT_PI_PAYLOAD); Long ethMacHash =
		 * Ethernet.toLong(eth.getSourceMACAddress());
		 * if(!macAddresses.contains(ethMacHash)){ macAddresses.add(ethMacHash);
		 * logger.info("MAC Address:{} seen on switch {}",
		 * HexString.toHexString(ethMacHash),sw.getId());
		 * 
		 * }
		 */
		// TODO Auto-generated method stub
		return null;
	}

	private OFPacketOut getPacketOut(IPacket udpPkt, short outport) {
		OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
				.getMessage(OFType.PACKET_OUT);
		// po.setInPort(pi.getInPort());
		po.setInPort(OFPort.OFPP_NONE);
		po.setBufferId(OFPacketOut.BUFFER_ID_NONE);

		// set actions
		OFActionOutput action = new OFActionOutput().setPort(outport);
		// FIXME:packet out to the inport??

		po.setActions(Collections.singletonList((OFAction) action));
		po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

		byte[] data = udpPkt.serialize();
		po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH + po.getActionsLength()
				+ data.length));
		po.setPacketData(data);
		return po;
	}

	private IPacket getCommandUDPPkt(String dstMac, String srcHostIP, short dstPort) {
		// construct a UDP
		IPacket testPacket = new Ethernet()
				.setDestinationMACAddress(dstMac)
				.setSourceMACAddress("00:11:22:33:44:55")
				.setEtherType(Ethernet.TYPE_IPv4)
				.setPayload(
						new IPv4()
								.setTtl((byte) 128)
								.setSourceAddress("192.168.4.136")
								.setDestinationAddress(srcHostIP)
								.setPayload(
										new UDP()
												.setSourcePort((short) 1234)
												.setDestinationPort((short) UDP_CMD_PORT)
												.setPayload(
														new Data("ACK".getBytes()))));
		return testPacket;
	}

	private void printL7Data(Data dataPkt) {
		System.out.println(dataPkt.getData().length);

		byte[] arr = dataPkt.getData();
		for (int i = 0; i < dataPkt.getData().length; i++) {
			System.out.print((char) arr[i]);
		}
	}

}
