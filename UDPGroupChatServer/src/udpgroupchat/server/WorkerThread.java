package udpgroupchat.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

public class WorkerThread extends Thread {

	private DatagramPacket rxPacket;
	private DatagramSocket socket;

	//private String clientID;
	//


	public WorkerThread(DatagramPacket packet, DatagramSocket socket) {
		this.rxPacket = packet;
		this.socket = socket;
	}

	@Override
	public void run() {
		// convert the rxPacket's payload to a string
		String payload = new String(rxPacket.getData(), 0, rxPacket.getLength())
		.trim();


		// dispatch request handler functions based on the payload's prefix

		if (payload.startsWith("REGISTER")) {
			onRegisterRequested(payload);
			return;
		}

		if (payload.startsWith("LISTGROUPS")) {
			onListGroupsRequested(payload);
			return;
		}

		if (payload.startsWith("LISTMYGROUPS")) {
			onListMyGroupsRequested(payload);
			return;
		}
		if(payload.startsWith("NAME"))
		{
			onNameRequested(payload);
			return;

		}
		if(payload.startsWith("JOIN"))
		{
			onJoinRequested(payload);
			return;
		}
		if(payload.startsWith("QUIT"))
		{
			onQuitRequested(payload);
			return;
		}
		if(payload.startsWith("MSG"))
		{
			onMsgRequested(payload);
			return;
		}
		if(payload.startsWith("POLL"))
		{
			onPollRequested(payload);
			return;
		}
		if(payload.startsWith("SHUTDOWN"))
		{
			onShutDownRequested(payload);
			return;
		}
		if(payload.startsWith("ACK"))
		{
			onACKReceived(payload);
			return;
		}

		// if we got here, it must have been a bad request, so we tell the
		// client about it
		onBadRequest(payload);
	}

	private void onListMyGroupsRequested(String payload) {
		String[] params= payload.split(",");
		String reply;
		String prefix = "";
		User user= null;

		if(params.length < 2)
		{
			reply = "+ERROR, Who is this? Need ID";
		}
		else{
			user = Server.clients.get(params[1]);
			if (user ==null)
			{
				reply = "+ERROR,invalid ClientID, recheck";
			}
			else
			{
				reply = "+SUCESS\n";
				if(params.length>3)
				{
					prefix = params[3];
				}
				for(Group group:Server.groups.values())
				{
					String delim ="";
					StringBuilder sb = new StringBuilder();
					if (group.members.contains(user)&&group.name.startsWith(prefix))
					{
						sb.append(delim);
						sb.append(group.name);
						delim = ",";
					}
					reply = "+SUCESS\n"+sb.toString();
				}
			}
		}
		try{
			send(reply, user);;
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void onListGroupsRequested(String payload) {
		String[] params= payload.split(",");
		String reply;
		String prefix = "";
		User user= null;

		if(params.length < 2)
		{
			reply = "+ERROR, Who is this? Need ID";
		}
		else{
			user = Server.clients.get(params[1]);
			if (user ==null)
			{
				reply = "+ERROR,invalid ClientID, recheck";
			}
			else
			{
				reply = "+SUCESS\n";
				if(params.length>3)
				{
					prefix = params[3];
				}
				for(String groupName:Server.groups.keySet())
				{
					String delim ="";
					StringBuilder sb = new StringBuilder();
					if (groupName.startsWith(prefix))
					{
						sb.append(delim);
						sb.append(groupName);
						delim = ",";
					}
					reply = "+SUCESS\n"+sb.toString();
				}
			}
		}
		try{
			send(reply, user);;
		} catch (IOException e) {
			e.printStackTrace();
		}




	}

	private void onACKReceived(String payload) {
		String[] params = payload.split(",");
		String reply;
		if (params.length < 2)
		{
			reply ="+ERROR,need  ACKid\n";
		}
		else{
			//can remove message since it's acknowleged. 
			ScheduledExecutorService executor = Server.pendingAck.get(params[1]);
			if (executor != null) 
			{	
				executor.shutdown();
				Server.pendingAck.remove(params[1]);
				reply="+SUCCESS" + params[1] +"Acknowleged\n";
			} 
			else {
				reply ="+ERROR,unknown ACKid\n";
			}
			try
			{
				send(reply,this.rxPacket.getAddress(),this.rxPacket.getPort());
			}catch(IOException e)
			{
				e.printStackTrace();
			}
		}

	}



	private void onShutDownRequested(String payload) {
		//are you localhost? 
		if (rxPacket.getAddress().isAnyLocalAddress() || rxPacket.getAddress().isLoopbackAddress())
		{

			//friendly warning. 
			try{
				send("Shutting Down Server\n", this.rxPacket.getAddress(),this.rxPacket.getPort());;
			} catch (IOException e) {
				e.printStackTrace();
			}
			socket.close();
		}

	}

	private void onMsgRequested(String payload) {
		String[] params= payload.split(",");
		String reply;
		User user= null;

		if(params.length < 2)
		{
			reply = "+ERROR, Who is this? Need ID";
		}
		else{
			user = Server.clients.get(params[1]);
			if (user ==null)
			{
				reply = "+ERROR,invalid ClientID, recheck";
			}
			else if (user.name == null) {
				reply = "+ERROR,Client needs a name. Use NAME cmd";
			}
			else if (params.length < 3) {
				reply = "+ERROR,Need Group to send to";
			}

			else if (params.length < 4) {
				reply= "+ERROR, message body empty";
			}
			else {
				String groupName = params[2];
				String message= "From " + user.name + "to " + groupName + ": ";
				Group group = Server.groups.get(groupName);

				if(group != null)
				{

					String delim = "";
					StringBuilder sb = new StringBuilder();
					for(int i=3; i<params.length; i++) {
						sb.append(delim);
						sb.append(params[i]);
						delim = ",";
					}

					message += sb.toString();
					//try to deliver message to client 
					for(User client:group.members)
					{
						try{
							send(message, client);
						} catch (Exception e) {
							//message not delivered. Save for later polling
							List<String>  messages = Server.messageQueue.get(params[1]);
							if(messages == null)
								messages = new LinkedList<String>();
							messages.add(message);
							Server.messageQueue.put(client.getID(),messages);
							e.printStackTrace();
						}
					}
					reply ="+SUCCESS,message sent to "+ groupName;
				}
				else
				{
					reply = "+ERROR,invalid group name";
				}

			}	
			try{
				send(reply, user);;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	//join group
	private void onJoinRequested(String payload) {
		String[] params= payload.split(",");
		String reply;
		User user= null;

		if(params.length < 2)
		{
			reply = "+ERROR, Who is this? Need ID";
		}
		else{
			user = Server.clients.get(params[1]);
			if (user ==null)
			{
				reply = "+ERROR,invalid ClientID, recheck";
			}
			else if (user.name == null) {
				reply = "+ERROR,Client needs a name. Use NAME cmd";
			}
			else if (params.length < 3) {
				reply = "+ERROR,no group specified. Check parameters";
			}
			else {
				String groupName = params[2];
				if (params.length > 3) {
					int maxMembers = Integer.parseInt(params[3]);
					if(Server.addClientToGroup(groupName, user , maxMembers))
					{
						reply= "+SUCCESS";
					}
					else
					{
						reply = "+ERROR,group full/client already in group";
					}
				}
				else
				{
					//use default group size 
					if(Server.addClientToGroup(groupName,user))
					{
						reply= "+SUCCESS,joined group " + groupName;
					}
					else
					{
						reply = "+ERROR,group full/client already in group";
					}
				}
			}
		}

		try{
			send(reply, user);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	//quit group
	private void onQuitRequested(String payload) {
		// TODO Auto-generated method stub
		String[] params= payload.split(",");
		String reply;
		User user= null;

		if(params.length < 2)
		{
			reply = "+ERROR, Who is this? Need ID";
		}
		else{
			user = Server.clients.get(params[1]);
			if (user ==null)
			{
				reply = "+ERROR,invalid ClientID, recheck";
			}
			else if (user.name == null) {
				reply = "+ERROR,Client nameless. Use NAME cmd";
			}
			else if (params.length < 3) {
				reply = "+ERROR,no group specified. Check parameters";
			}
			else {
				String groupName = params[2];

				if(Server.removeClientFromGroup(groupName, user))
				{
					reply= "+SUCCESS";
				}
				else
				{
					reply = "+ERROR,client not in group";
				}

			}
		}

		try{
			send(reply, user);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}



	private void onNameRequested(String payload) {
		String[] params= payload.split(",");
		String reply;
		User user = null;

		if(params.length < 2)
		{
			reply = "Who is this? Need clientID and name";
		}
		else if(params.length < 3){
			reply = "name not specified";
		}
		else
		{
			user = Server.clients.get(params[1]);
			if (user ==null)
			{
				reply = "+ERROR,invalid ClientID, recheck";
			}

			else if(user.name != null)
			{
				reply = "Already have a name";
			}
			else if(Server.names.contains(params[2])){
				reply = "name already taken";
			}
			else 
			{
				//success
				user.name = params[2];
				Server.names.add(user.name);
				reply = "+SUCCESS";
			}
		}
		try{
			send((reply.equals("+SUCCESS")?reply:"+ERROR,"+ reply),user);
		} catch (IOException e) {
			e.printStackTrace();
		}


	}

	private void onPollRequested(String payload) {
		String[] params = payload.split(",");
		User user =null;
		String reply ="+ERROR,no such client";
		if( params.length > 1 && Server.clients.containsKey(params[1]))
		{
			//get pending messages for this client. 
			List<String> messages = Server.messageQueue.get(params[1]);
			user = Server.clients.get(params[1]);
			if (user!=null && messages!= null) {
				for (String message: messages) {

					try{
						send(message, user);
						messages.remove(message);
					}
					catch (IOException e) {
						e.printStackTrace();
					}

				}
				if (messages.isEmpty())
					reply = "+SUCCESS,all messages sent";
			}else{
				reply = "+SUCCESS,no messages";
			}

		}
		try{
			send(reply, user);
		}
		catch (IOException e) {
			e.printStackTrace();
		}


	}





	// send a string, wrapped in a UDP packet, to the specified remote endpoint
	public void send(String payload, InetAddress address, int port)
			throws IOException {
		DatagramPacket txPacket = new DatagramPacket(payload.getBytes(),
				payload.length(), address, port);
		this.socket.send(txPacket);
	}

	// send a string, wrapped in a UDP packet, to the specified user every 10 seconds until acknowledged
	public void send(final String payload, final User user) throws IOException {
		if(user != null)
		{
			String ackID = "" + user.getID()  + ":" + user.currentReqID.incrementAndGet();
			// Append the ID + reuestID to the beginning of the payload
			final String sendPayload = ackID + " " + payload +"\n";
			ScheduledExecutorService executor = Executors
					.newSingleThreadScheduledExecutor();
			Runnable sendTask = new Runnable() {
				public void run() {
					DatagramPacket txPacket = new DatagramPacket(
							sendPayload.getBytes(), sendPayload.length(), user.endpoint.address, user.endpoint.port);

					try {
						socket.send(txPacket);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						throw new RuntimeException();							}


				}
			};

			//try to send message every ten second
			executor.scheduleAtFixedRate(sendTask, 0, 10,
					SECONDS);
			//put in ack map
			Server.pendingAck.put(ackID,executor);
		}	
		else{
			//not a user yet //no ack mechanism
			send(payload+"\n",this.rxPacket.getAddress(),this.rxPacket.getPort());
		}
	}

	private void onRegisterRequested(String payload) {
		// get the address of the sender from the rxPacket
		InetAddress address = this.rxPacket.getAddress();
		// get the port of the sender from the rxPacket
		int port = this.rxPacket.getPort();

		String[] params= payload.split(",");
		User user = null;


		//client has changed it's endpoint;
		if(params.length>1 && Server.clients.containsKey(params[1]))
		{
			user = Server.clients.get(params[1]);
			user.endpoint = new ClientEndPoint(address, port);;

		}
		else
		{
			//first timer 
			user = new User(new ClientEndPoint(address,port));
			Server.clients.put(user.getID().toString(), user );
		}


		// tell client we're OK
		try {
			send("REGISTERED",user);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	private void onBadRequest(String payload) {
		try {
			send("BAD REQUEST\n", this.rxPacket.getAddress(),
					this.rxPacket.getPort());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
