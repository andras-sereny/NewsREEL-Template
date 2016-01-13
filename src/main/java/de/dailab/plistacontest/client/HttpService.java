package de.dailab.plistacontest.client;

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
    

	public Object servePost(Request request, Response response) {
		String responseText = "";
		if (request.contentLength() <= 0) {
			System.out.println("[INFO] Initial Message with no content received." );
			response(response, request, null, false);
		}
		else {
			Map<String, String> parameters = request.params();
			String typeMessage = parameters.get("type");
			String bodyMessage = parameters.get("body");

			// we may recode the body message
//			if (_breq.getContentType().equals("application/x-www-form-urlencoded; charset=utf-8")) {
//				bodyMessage = URLDecoder.decode(bodyMessage, "utf-8");
//			}

			responseText = handleMessage(typeMessage, bodyMessage);
			response(response, request, responseText, true);
		}
		return responseText;
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
