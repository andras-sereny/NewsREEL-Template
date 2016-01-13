package de.dailab.plistacontest.client;


import static spark.Spark.get;
import static spark.Spark.post;
import static spark.SparkBase.port;
import static spark.SparkBase.staticFileLocation;

public class Main {

	private static HttpService service = new HttpService();

	public static void main(String[] args) {

		port(Integer.valueOf(System.getenv("PORT")));
		staticFileLocation("/public");

		post("/", (request, response) -> service.servePost(request, response));

		get("/", (request, response) -> service.serveGet(request, response));
		get("/hello", (request, response) -> service.serveGet(request, response));

	}

}
