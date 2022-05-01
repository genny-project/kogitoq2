package life.genny.fyodor.endpoints;

import io.vertx.core.http.HttpServerRequest;
import java.util.List;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import life.genny.qwandaq.Ask;
import life.genny.qwandaq.message.QDataAskMessage;
import life.genny.qwandaq.models.GennyToken;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.DatabaseUtils;
import life.genny.qwandaq.utils.QuestionUtils;
import life.genny.serviceq.Service;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.jaxrs.PathParam;






/**
 * Entities --- Endpoints providing database entity access
 *
 * @author jasper.robison@gada.io
 *
 */
@Path("/qwanda")
public class QwandaEndpoint {

	private static final Logger log = Logger.getLogger(QwandaEndpoint.class);

	static Jsonb jsonb = JsonbBuilder.create();

	@Context
	HttpServerRequest request;

	@Inject
	DatabaseUtils databaseUtils;

	@Inject
	Service service;

	@Inject
	QuestionUtils questionUtils;


	@GET
	@Consumes("application/json")
	@Path("/baseentitys/{sourceCode}/asks2" + "/{questionCode}/{targetCode}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response createAsks3(@PathParam("sourceCode") final String sourceCode,
			@PathParam("questionCode") final String questionCode, @PathParam("targetCode") final String targetCode,
			@Context final UriInfo uriInfo) {

				String token = null;

		GennyToken userToken = null;
		try {
			token = request.getHeader("authorization").split("Bearer ")[1];
			if (token != null) {
				userToken = new GennyToken(token);
			} else {
				log.error("Bad token in Search GET provided");
				return Response.status(Response.Status.FORBIDDEN).build();
			}
		} catch (Exception e) {
			log.error("Bad or no header token in Search POST provided");
			return Response.status(Response.Status.BAD_REQUEST).build();
		}
	

		BaseEntityUtils beUtils = service.getBeUtils();
		beUtils.getServiceToken().setProjectCode(userToken.getRealm());
		beUtils.setGennyToken(userToken);
		List<Ask> asks = questionUtils.createAsksByQuestionCode2(questionCode, sourceCode, targetCode, beUtils);
	
		log.debug("Number of asks=" + asks.size());
		log.debug("Number of asks=" + asks);
		final QDataAskMessage askMsgs = new QDataAskMessage(asks.toArray(new Ask[0]));
		askMsgs.setToken(userToken.getToken());
		String json = jsonb.toJson(askMsgs);
		return Response.status(200).entity(json).build();
	}

}
