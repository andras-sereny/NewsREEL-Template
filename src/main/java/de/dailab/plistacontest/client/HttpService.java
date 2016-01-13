package de.dailab.plistacontest.client;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;

public class HttpService {
	
	/**
     * Define the default logger
     */
    private final static Logger logger = LoggerFactory.getLogger(HttpService.class);

    /**
     * here we store all relevant data about items.
     */
    private final RecommenderItemTable recommenderItemTable = new RecommenderItemTable();
    

	private Map<String, String> parseParameters(String text) {
		Map<String,String> paramsMap = new HashMap<>();
		String[] elements = text.split("&");
		for (String element: elements) {
			String[] keyValue = element.split("=");
			paramsMap.put(keyValue[0], keyValue[1]);
		}
		return paramsMap;
	}

	public Object servePost(Request request, Response response) {
		String responseText = "";
		if (request.contentLength() <= 0) {
			System.out.println("[INFO] Initial Message with no content received." );
			response(response, request, null, false);
		}
		else {
			Map<String, String> parameters = parseParameters(request.body());


			String typeMessage = parameters.get("type");
			String bodyMessage = parameters.get("body");
			String propertyMessage = parameters.get("properties");
			String entityMessage = parameters.get("entities");

			// handle idomaar messages
			if (bodyMessage == null || bodyMessage.length() == 0) {
				if (request.contentType().equalsIgnoreCase("application/x-www-form-urlencoded; charset=utf-8")) {
					try {
						//bodyMessage = URLDecoder.decode(bodyMessage, "utf-8");
						propertyMessage = URLDecoder.decode(propertyMessage, "utf-8");
						entityMessage = URLDecoder.decode(entityMessage, "utf-8");
					} catch (UnsupportedEncodingException exception) {
						throw new RuntimeException(exception);
					}
				}
				responseText = handleIdomaarMessage(typeMessage, propertyMessage, entityMessage);
				response(response, request, responseText, true);
			}
		}
		return responseText;
	}

	private String handleIdomaarMessage(final String messageType, final String properties, final String entities) {
		// write all data from the server to a file
		// logger.info(messageType + "\t" + properties + "\t" + entities);

		// create an jSON object from the String
		final JSONObject jOP = (JSONObject) JSONValue.parse(properties);
		final JSONObject jOE = (JSONObject) JSONValue.parse(entities);

		// merge the different jsonObjects and correct missing itemIDs
		jOP.putAll(jOE);
		Object itemID = jOP.get("itemID");
		if (itemID == null) {
			jOP.put("itemID", 0);
		}

		// define a response object
		String response = null;

		if ("impression".equalsIgnoreCase(messageType) || "recommendation".equalsIgnoreCase(messageType)) {

			// parse the type of the event
			final RecommenderItem item = RecommenderItem.parseEventNotification(jOP.toJSONString());
			final String eventNotificationType = messageType;

			// impression refers to articles read by the user
			if ("impression".equalsIgnoreCase(eventNotificationType) || "recommendation".equalsIgnoreCase(eventNotificationType)) {

				// we mark this information in the article table
				if (item.getItemID() != null) {
					// new items shall be added to the list of items
					recommenderItemTable.handleItemUpdate(item);
					item.setNumberOfRequestedResults(6);

					response = "handle impression eventNotification successful";

					boolean recommendationExpected = false;
					if (properties.contains("\"event_type\": \"recommendation_request\"")) {
						recommendationExpected = true;
					}
					if (recommendationExpected) {
						List<Long> suggestedItemIDs = recommenderItemTable.getLastItems(item);
						response = "{" + "\"recs\": {" + "\"ints\": {" + "\"3\": " + suggestedItemIDs + "}" + "}}";
					}

				}
				// click refers to recommendations clicked by the user
			} else if ("click".equalsIgnoreCase(eventNotificationType)) {

				// we mark this information in the article table
				if (item.getItemID() != null) {
					// new items shall be added to the list of items
					recommenderItemTable.handleItemUpdate(item);

					response = "handle impression eventNotification successful";
				}
				response = "handle click eventNotification successful";

			} else {
				System.out.println("unknown event-type: "
						+ eventNotificationType + " (message ignored)");
			}

		} else if ("error_notification".equalsIgnoreCase(messageType)) {

			System.out.println("error-notification: " + jOP.toString() + jOE.toJSONString());

		} else {
			System.out.println("unknown MessageType: " + messageType);
			// Error handling
			logger.info(jOP.toString() + jOE.toJSONString());
			// this.contestRecommender.error(jObj.toString());
		}
		return response;
	}


	private String response(Response response, Request request, String text, boolean b) {
		response.header("Content-Type", "text/html;charset=utf-8");
		response.status(HttpServletResponse.SC_OK);
		if (text != null && b) {
			response.body(text);
		}
		return text;
	}
	
    private String handleMessage(final String messageType, final String _jsonMessageBody) {

        // write all data from the server to a file
        logger.info(messageType + "\t" + _jsonMessageBody);
    	
        // create an jSON object from the String 
        final JSONObject jObj = (JSONObject) JSONValue.parse(_jsonMessageBody);

        // define a response object 
        String response = null;
        
        // TODO handle "item_create"

        // in a complex if/switch statement we handle the differentTypes of messages
        if ("item_update".equalsIgnoreCase(messageType)) {
            
        	// we extract itemID, domainID, text and the timeTime, create/update
        	final RecommenderItem recommenderItem = RecommenderItem.parseItemUpdate(_jsonMessageBody);
        	
        	// we mark this information in the article table
        	if (recommenderItem.getItemID() != null) {
        		recommenderItemTable.handleItemUpdate(recommenderItem);
        	}
        	
        	response = ";item_update successfull";
        } 
        
        else if ("recommendation_request".equalsIgnoreCase(messageType)) {

        	// we handle a recommendation request
        	try {
        	    // parse the new recommender request
        		RecommenderItem currentRequest = RecommenderItem.parseRecommendationRequest(_jsonMessageBody);
        		
        		// gather the items to be recommended
        		List<Long> resultList = recommenderItemTable.getLastItems(currentRequest);
        		if (resultList == null) {
        			response = "[]";
        			System.out.println("invalid resultList");
        		} else {
        			response = resultList.toString();
        		}
        		response = getRecommendationResultJSON(response);
        		
        	    // TODO? might handle the the request as impressions
        	} catch (Throwable t) {
        		t.printStackTrace();
        	}
        }
        else if ("event_notification".equalsIgnoreCase(messageType)) {
        	
        	// parse the type of the event
        	final RecommenderItem item = RecommenderItem.parseEventNotification(_jsonMessageBody);
    		final String eventNotificationType = item.getNotificationType();
    		
            // impression refers to articles read by the user
    		if ("impression".equalsIgnoreCase(eventNotificationType)) {
    			            	                
					// we mark this information in the article table
		        	if (item.getItemID() != null) {
                        // new items shall be added to the list of items
		        		recommenderItemTable.handleItemUpdate(item);
		        	
					response = "handle impression eventNotification successful";
				}
            // click refers to recommendations clicked by the user
    		} else if ("click".equalsIgnoreCase(eventNotificationType)) { 
 
    			response = "handle click eventNotification successful";
    			
    		} else {
    			System.out.println("unknown event-type: " + eventNotificationType + " (message ignored)");
    		}
            
        } else if ("error_notification".equalsIgnoreCase(messageType)) {
        	
        	System.out.println("error-notification: " + _jsonMessageBody);
        	
        } else {
        	System.out.println("unknown MessageType: " + messageType);
            // Error handling
            logger.info(jObj.toString());
            //this.contestRecommender.error(jObj.toString());
        }
        return response;
    }


    /**
	 * Create a json response object for recommendation requests.
	 * @param _itemsIDs a list as string
	 * @return json
	 */
	public static final String getRecommendationResultJSON(String _itemsIDs) {
		
		// TODO log invalid results
		if (_itemsIDs == null ||_itemsIDs.length() == 0) {
			_itemsIDs = "[]";
		} else if (!_itemsIDs.trim().startsWith("[")) {
			_itemsIDs = "[" + _itemsIDs + "]";
		}
		// build result as JSON according to formal requirements
        String result = "{" + "\"recs\": {" + "\"ints\": {" + "\"3\": "
				+ _itemsIDs + "}" + "}}";

		return result;
	}

	public Object serveGet(Request request, Response response) {
		return response(response, request, "Server up. Visit <h3><a href=\"http://www.gravityrd.com\">the Gravity page</a></h3>", true);
	}

}
