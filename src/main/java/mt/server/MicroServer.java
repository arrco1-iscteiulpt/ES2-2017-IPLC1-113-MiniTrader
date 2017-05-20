package mt.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import mt.Order;
import mt.client.Session;
import mt.comm.ServerComm;
import mt.comm.ServerSideMessage;
import mt.comm.impl.ServerCommImpl;
import mt.exception.ServerException;
import mt.filter.AnalyticsFilter;

/**
 * MicroTraderServer implementation. This class should be responsible
 * to do the business logic of stock transactions between buyers and sellers.
 * 
 * @author Group 78
 *
 */
//test commit
public class MicroServer implements MicroTraderServer {
	
	public static void main(String[] args) {
		ServerComm serverComm = new AnalyticsFilter(new ServerCommImpl());
		MicroTraderServer server = new MicroServer();
		server.start(serverComm);
	}

	public static final Logger LOGGER = Logger.getLogger(MicroServer.class.getName());

	/**
	 * Server communication
	 */
	private ServerComm serverComm;

	/**
	 * A map to sore clients and clients orders
	 */
	private Map<String, Set<Order>> orderMap;

	/**
	 * Orders that we must track in order to notify clients
	 */
	private Set<Order> updatedOrders;

	/**
	 * Order Server ID
	 */
	private static int id = 1;
	
	/** The value is {@value #EMPTY} */
	public static final int EMPTY = 0;

	/**
	 * Constructor
	 */
	public MicroServer() {
		LOGGER.log(Level.INFO, "Creating the server...");
		orderMap = new HashMap<String, Set<Order>>();
		updatedOrders = new HashSet<>();
	}

	@Override
	public void start(ServerComm serverComm) {
		serverComm.start();
		
		LOGGER.log(Level.INFO, "Starting Server...");

		this.serverComm = serverComm;

		ServerSideMessage msg = null;
		while ((msg = serverComm.getNextMessage()) != null) {
			ServerSideMessage.Type type = msg.getType();
			
			if(type == null){
				serverComm.sendError(null, "Type was not recognized");
				continue;
			}

			switch (type) {
				case CONNECTED:
					try{
						processUserConnected(msg);
					}catch (ServerException e) {
						serverComm.sendError(msg.getSenderNickname(), e.getMessage());
					}
					break;
				case DISCONNECTED:
					processUserDisconnected(msg);
					break;
				case NEW_ORDER:
					try {
						verifyUserConnected(msg);
						if(msg.getOrder().getServerOrderID() == EMPTY)
							msg.getOrder().setServerOrderID(id++);
						if(unitsMoreThan10(msg.getOrder()))
							throw new ServerException("The quantity of the order must be greater than 10 units");
						if(sameSellerOrBuyeOrder(msg.getOrder()))
							throw new ServerException("Clients are not allowed to issue sell orders for their own buy orders and vice versa ");
						
						maxSellOrders(msg.getOrder());
						notifyAllClients(msg.getOrder());
						processNewOrder(msg);
						
					}
				 catch (ServerException e) {
						serverComm.sendError(msg.getSenderNickname(), e.getMessage());
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					break;
				default:
					break;
				}
		}
		LOGGER.log(Level.INFO, "Shutting Down Server...");
	}


	/**
	 * Verify if user is already connected
	 * 
	 * @param msg
	 * 			the message sent by the client
	 * @throws ServerException
	 * 			exception thrown by the server indicating that the user is not connected
	 */
	private void verifyUserConnected(ServerSideMessage msg) throws ServerException {
		for (Entry<String, Set<Order>> entry : orderMap.entrySet()) {
			if(entry.getKey().equals(msg.getSenderNickname())){
				return;
			}
		}
		throw new ServerException("The user " + msg.getSenderNickname() + " is not connected.");
		
	}

	/**
	 * Process the user connection
	 * 
	 * @param msg
	 * 			  the message sent by the client
	 * 
	 * @throws ServerException
	 * 			exception thrown by the server indicating that the user is already connected
	 */
	private void processUserConnected(ServerSideMessage msg) throws ServerException {
		LOGGER.log(Level.INFO, "Connecting client " + msg.getSenderNickname() + "...");
		
		// verify if user is already connected
		for (Entry<String, Set<Order>> entry : orderMap.entrySet()) {
			if(entry.getKey().equals(msg.getSenderNickname())){
				throw new ServerException("The user " + msg.getSenderNickname() + " is already connected.");
			}
		}
		
		// register the new user
		orderMap.put(msg.getSenderNickname(), new HashSet<Order>());
		
		notifyClientsOfCurrentActiveOrders(msg);
	}
	
	/**
	 * Send current active orders sorted by server ID ASC
	 * @param msg
	 */
	private void notifyClientsOfCurrentActiveOrders(ServerSideMessage msg) {
		List<Order> ordersToSend = new ArrayList<>();
		// update the new registered user of all active orders
		for (Entry<String, Set<Order>> entry : orderMap.entrySet()) {
			Set<Order> orders = entry.getValue();
			for (Order order : orders) {
				ordersToSend.add(order);
			}
		}
		
		// sort the orders to send to clients by server id
		Collections.sort(ordersToSend, new Comparator<Order>() {
			@Override
			public int compare(Order o1, Order o2) {
				return o1.getServerOrderID() < o2.getServerOrderID() ? -1 : 1;
			}
		});
		
		for(Order order : ordersToSend){
			serverComm.sendOrder(msg.getSenderNickname(), order);
		}
	}

	/**
	 * Process the user disconnection
	 * 
	 * @param msg
	 * 			  the message sent by the client
	 */
	private void processUserDisconnected(ServerSideMessage msg) {
		LOGGER.log(Level.INFO, "Disconnecting client " + msg.getSenderNickname()+ "...");
		
		//remove the client orders
		orderMap.remove(msg.getSenderNickname());
		
		// notify all clients of current unfulfilled orders
		for (Entry<String, Set<Order>> entry : orderMap.entrySet()) {
			Set<Order> orders = entry.getValue();
			for (Order order : orders) {
				serverComm.sendOrder(msg.getSenderNickname(), order);
			}
		}
	}

	/**
	 * Process the new received order
	 * 
	 * @param msg
	 *            the message sent by the client
	 */
	private void processNewOrder(ServerSideMessage msg) throws ServerException {
		LOGGER.log(Level.INFO, "Processing new order...");

		Order o = msg.getOrder();
		try {
				// save the order on map
				saveOrder(o);
				// if is buy order
				if (o.isBuyOrder()) {
					processBuy(msg.getOrder());
				}
			
				// if is sell order
				if (o.isSellOrder()) {
					processSell(msg.getOrder());
				}

				// notify clients of changed order
				notifyClientsOfChangedOrders();

				// remove all fulfilled orders
				removeFulfilledOrders();

				// reset the set of changed orders
				updatedOrders = new HashSet<>();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	/**
	 *  Os clientes n�o est�o autorizados a
	 *   emitir ordens de venda para as 
	 *   suas pr�prias ordens de compra e vice-versa. 
	 */
	private boolean sameSellerOrBuyeOrder(Order o) throws Exception{
		/*
		 * Este metodo pecorre todas as ordens, verificando se o user 
		 *  tem a mesma ordem para uma compra ou a mesma ordem para uma venda, 
		 *  verificando se os nomes s�o iguais, 
		 * 
		 * 
		 * 
		 */
		Set<Order> orders = orderMap.get(o.getNickname());
		for (Iterator<Order> it = orders.iterator(); it.hasNext(); ) {
			Order order = it.next();
			if(order.isSellOrder()){
				if(o.isBuyOrder()){
					if(order.getStock().equals(o.getStock()))
						return true;		
				}
			}else if(order.isBuyOrder()){
				if(o.isSellOrder()){
					if(order.getStock().equals(o.getStock()))
						return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Uma quantidade de ordem �nica
	 * (compra ou ordem de venda) nunca 
	 * pode ser inferior a 10 unidades 
	 */
	private boolean unitsMoreThan10(Order order) throws Exception{
		if(order.getNumberOfUnits() < 10 )
			return true;
		else 
			return false;
	}
	
	/**
	 * Store the order on map
	 * 
	 * @param o
	 * 			the order to be stored on map
	 */
	private void saveOrder(Order o) {
		LOGGER.log(Level.INFO, "Storing the new order...");
		
		//save order on map
		Set<Order> orders = orderMap.get(o.getNickname());
		orders.add(o);		
	}

	/**
	 * Process the sell order
	 * 
	 * @param sellOrder
	 * 		Order sent by the client with a number of units of a stock and the price per unit he wants to sell
	 */
	private void processSell(Order sellOrder){
		LOGGER.log(Level.INFO, "Processing sell order...");
		
		for (Entry<String, Set<Order>> entry : orderMap.entrySet()) {
			for (Order o : entry.getValue()) {
				if (o.isBuyOrder() && o.getStock().equals(sellOrder.getStock()) && o.getPricePerUnit() >= sellOrder.getPricePerUnit()) {
					doTransaction (o, sellOrder);
				}
			}
		}
		
	}
	
	/**
	 * Process the buy order
	 * 
	 * @param buyOrder
	 *          Order sent by the client with a number of units of a stock and the price per unit he wants to buy
	 */
	private void processBuy(Order buyOrder) {
		LOGGER.log(Level.INFO, "Processing buy order...");

		for (Entry<String, Set<Order>> entry : orderMap.entrySet()) {
			for (Order o : entry.getValue()) {
				if (o.isSellOrder() && buyOrder.getStock().equals(o.getStock()) && o.getPricePerUnit() <= buyOrder.getPricePerUnit()) {
					doTransaction(buyOrder, o);
				}
			}
		}

	}

	/**
	 * Process the transaction between buyer and seller
	 * 
	 * @param buyOrder 		Order sent by the client with a number of units of a stock and the price per unit he wants to buy 
	 * @param sellerOrder	Order sent by the client with a number of units of a stock and the price per unit he wants to sell
	 */
	private void doTransaction(Order buyOrder, Order sellerOrder) {
		LOGGER.log(Level.INFO, "Processing transaction between seller and buyer...");

		if (buyOrder.getNumberOfUnits() >= sellerOrder.getNumberOfUnits()) {
			buyOrder.setNumberOfUnits(buyOrder.getNumberOfUnits()
					- sellerOrder.getNumberOfUnits());
			sellerOrder.setNumberOfUnits(EMPTY);
		} else {
			sellerOrder.setNumberOfUnits(sellerOrder.getNumberOfUnits()
					- buyOrder.getNumberOfUnits());
			buyOrder.setNumberOfUnits(EMPTY);
		}
		
		updatedOrders.add(buyOrder);
		updatedOrders.add(sellerOrder);
	}
	
	/**
	 * Notifies clients about a changed order
	 * 
	 * @throws ServerException
	 * 			exception thrown in the method notifyAllClients, in case there's no order
	 */
	private void notifyClientsOfChangedOrders() throws ServerException {
		LOGGER.log(Level.INFO, "Notifying client about the changed order...");
		for (Order order : updatedOrders){
			notifyAllClients(order);
		}
	}
	
	/**
	 * Notifies all clients about a new order
	 * 
	 * @param order refers to a client buy order or a sell order
	 * @throws ServerException
	 * 				exception thrown by the server indicating that there is no order
	 */			
	private void notifyAllClients(Order order) throws ServerException {
		LOGGER.log(Level.INFO, "Notifying clients about the new order...");
		if(order == null){
			throw new ServerException("There was no order in the message");
		}
		for (Entry<String, Set<Order>> entry : orderMap.entrySet()) {
			serverComm.sendOrder(entry.getKey(), order); 
		}
	}
	
	/**
	 * Remove fulfilled orders
	 */
	private void removeFulfilledOrders() {
		LOGGER.log(Level.INFO, "Removing fulfilled orders...");
		
		// remove fulfilled orders
		for (Entry<String, Set<Order>> entry : orderMap.entrySet()) {
			Iterator<Order> it = entry.getValue().iterator();
			while (it.hasNext()) {
				Order o = it.next();
				if (o.getNumberOfUnits() == EMPTY) {
					it.remove();
				}
			}
		}
	}
	private void maxSellOrders(Order o) throws ServerException{
		int i= 0;
		/*
		 * Este metodo vai percorrer todas as ordens do tipo sell do user e lan�a
		 * uma mensagem se atingir o numero de 5 ordens de venda
		 */
		Set<Order> orders = orderMap.get(o.getNickname());
		for (Iterator<Order> it = orders.iterator(); it.hasNext(); ) {
			Order order = it.next();
			if(order.isSellOrder())
			i++;
	
		}
		if(i == 5)
			throw new ServerException("You can't have more than 5 unfilled orders");
			
	}
	
}
