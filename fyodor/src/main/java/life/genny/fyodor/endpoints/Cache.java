package life.genny.fyodor.endpoints;

import io.vertx.core.http.HttpServerRequest;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.utils.CacheUtils;
import life.genny.qwandaq.utils.DatabaseUtils;
import life.genny.qwandaq.utils.HttpUtils;
import life.genny.serviceq.Service;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.jaxrs.PathParam;




/**
 * Cache --- Endpoints providing cache access
 *
 * @author jasper.robison@gada.io
 *
 */
@Path("/cache")
public class Cache {

	private static final Logger log = Logger.getLogger(Cache.class);

	static Jsonb jsonb = JsonbBuilder.create();

	@Context
	HttpServerRequest request;

	@Inject
	Service service;

	@Inject
	UserToken userToken;

	@Inject
	DatabaseUtils databaseUtils;


	/**
	* Read an item from the cache.
	*
	* @param productCode The productCode of the cache item
	* @param key The key of the cache item
	* @return The json item
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{productCode}/{key}")
	public Response readProductCodeKey(@PathParam("productCode") String productCode,@PathParam("key") String key) {
		log.info("[!] read(" + productCode+":"+key + ")");
		if (userToken == null) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(HttpUtils.error("Not authorized to make this request")).build();
		}
		
		if (!"service".equals(userToken.getUsername()) && !userToken.hasRole("test")) {
			return Response.status(Response.Status.BAD_REQUEST)
				.entity(HttpUtils.error("User not authorized to make this request")).build();
		}

		log.info("User: " + userToken.getUserCode());
		log.info("Product Code/Cache: " + productCode);
		String json = (String) CacheUtils.readCache(productCode, key);

		if (StringUtils.isBlank(json)) {
			log.info("Could not find in cache: " + key);
			
			return Response.ok("null").build();
		}

		log.info("Found json of length " + json.length() + " for " + key);

		return Response.ok(json).build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{productCode}/{key}")
	public Response write(@PathParam("productCode") String productCode,@PathParam("key") String key, String value) {
		log.info("[!] write(" + productCode+":"+key + ":"+value+")");
		if (userToken == null) {
			return Response.status(Response.Status.BAD_REQUEST)
				.entity(HttpUtils.error("Not authorized to make this request")).build();
		}

		if (!"service".equals(userToken.getUsername())) {
			return Response.status(Response.Status.BAD_REQUEST)
				.entity(HttpUtils.error("User not authorized to make this request")).build();
		}


		CacheUtils.writeCache(productCode, key, value);

		log.info("Wrote json of length " + value.length() + " for " + key);

		return Response.ok().build();
	}

	/**
	* Read an item from the cache.
	*
	* @param key The key of the cache item
	* @return The json item
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{key}")
	public Response read(@PathParam("key") String key) {
		log.info("[!] read(" + key + ")");
		if (userToken == null) {
			return Response.status(Response.Status.BAD_REQUEST)
				.entity(HttpUtils.error("Not authorized to make this request")).build();
		}

		log.info("User: " + userToken.getUserCode());
		log.info("Product Code/Cache: " + userToken.getProductCode());
		String productCode = userToken.getProductCode();
		String json = (String) CacheUtils.readCache(productCode, key);

		if (json == null) {
			log.info("Could not find in cache: " + key);
			return Response.ok("null").build();
		}

		log.info("Found json of length " + json.length() + " for " + key);

		return Response.ok(json).build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{key}")
	public Response write(@PathParam("key") String key, String value) {
		log.info("Writing to cache " + userToken.getProductCode() + ": [" + key + ":" + value + "]");
		if (userToken == null) {
			return Response.status(Response.Status.BAD_REQUEST)
				.entity(HttpUtils.error("Not authorized to make this request")).build();
		}

		String productCode = userToken.getProductCode();
		CacheUtils.writeCache(productCode, key, value);

		log.info("Wrote json of length " + value.length() + " for " + key);

		return Response.ok().build();
	}

	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{key}")
	public Response remove(@PathParam("key") String key) {

		if (userToken == null) {
			return Response.status(Response.Status.BAD_REQUEST)
				.entity(HttpUtils.error("Not authorized to make this request")).build();
		}

		String productCode = userToken.getProductCode();
		CacheUtils.removeEntry(productCode, key);

		log.info("Removed Item for " + key);

		return Response.ok().build();
	}

}
