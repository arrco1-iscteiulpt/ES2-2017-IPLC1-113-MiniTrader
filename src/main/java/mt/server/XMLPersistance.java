package mt.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import mt.Order;

/**
 * 
 * @author WorteX
 *
 */

public class XMLPersistance {

	private final String fileName = "OrderPresistance.xml";
	
	/**
	 * This method writes/appends an order to the xml file.
	 * 
	 * @param order that will be saved on xml file
	 * @throws ParserConfigurationException indicates a serious configuration error.
	 * @throws SAXException if the method can't parse the xml file
	 * @throws IOException if the method can't write on xml file
	 */
	public void saveOrderToXML(Order order) throws ParserConfigurationException, SAXException, IOException{
		File file = new File(fileName);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document document = null;
		Element root = null;
		boolean fileExists = true;
		if(file.exists()){
			document = dBuilder.parse(file);
			root = document.getDocumentElement();
			fileExists = false;
		}else{
			document = dBuilder.newDocument();
			root = document.createElement("Orders");
		}
		
		Element orderElement = storeOrderData(document, document.createElement("Order"), order);
		
		root.appendChild(orderElement);
		
		if(fileExists)
			document.appendChild(root);
		
		try {
			saveDataToXMLFile(document);
		} catch (TransformerException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * This method writes/appends the document to a XML file
	 * 
	 * @param document representation of XML file in java class.
	 * @throws TransformerException that specifies an exceptional condition that occured during the transformation process.
	 * @throws FileNotFoundException if the output file doesnt exists
	 */
	private void saveDataToXMLFile(Document document) throws TransformerException, FileNotFoundException{
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		StreamResult result = new StreamResult(new FileOutputStream(fileName));
		DOMSource source = new DOMSource(document);
		transformer.transform(source, result);
	}
	
	/**
	 * This method saves the order data into Element class that will be appended to the root Node.
	 * 
	 * @param document representation of XML file
	 * @param orderElement is the root Node of the order that is going to be appended
	 * @param order that is going to be appended to the Node
	 * @return Element that represents the Node that contains the order
	 */
	private Element storeOrderData(Document document, Element orderElement,Order order){
		orderElement.setAttribute("ServerOrderID", ""+order.getServerOrderID());
		Element operation = document.createElement("operation");
		if(order.isBuyOrder())
			operation.appendChild(document.createTextNode("buy"));
		else
			operation.appendChild(document.createTextNode("sell"));
		orderElement.appendChild(operation);
		Element stock = document.createElement("stock");
		stock.appendChild(document.createTextNode(order.getStock()));
		orderElement.appendChild(stock);
		Element numberUnits = document.createElement("numberOfUnits");
		numberUnits.appendChild(document.createTextNode(""+order.getNumberOfUnits()));
		orderElement.appendChild(numberUnits);
		Element priceUnits = document.createElement("pricePerUnits");
		priceUnits.appendChild(document.createTextNode(""+order.getPricePerUnit()));
		orderElement.appendChild(priceUnits);
		Element isBuyOrder = document.createElement("isBuyOrder");
		isBuyOrder.appendChild(document.createTextNode(""+order.isBuyOrder()));
		orderElement.appendChild(isBuyOrder);
		return orderElement;
	}
	
}
