package io.kaoto.backend.api.resource;

import io.kaoto.backend.api.resource.request.DeploymentResourceYamlRequest;
import io.kaoto.backend.api.service.deployment.DeploymentService;
import io.kaoto.backend.deployment.ClusterService;
import io.kaoto.backend.model.deployment.Integration;
import io.smallrye.config.ConfigMapping;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * 🐱class DeploymentResource
 * 🐱relationship compositionOf DeploymentService, 0..1
 *
 * This endpoint will return the yaml needed to deploy
 * the related integration and the
 * endpoints to interact with deployments.
 */
@Path("/integrations")
@ApplicationScoped
public class IntegrationResource {

    private Logger log = Logger.getLogger(IntegrationResource.class);


    @Inject
    public void setDeploymentService(
            final DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @Inject
    public void setClusterService(
            final ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    public void setCrdDefault(final String crdDefault) {
        this.crdDefault = crdDefault;
    }

    @ConfigMapping(prefix = "crd.default")
    private String crdDefault;
    private DeploymentService deploymentService;
    private ClusterService clusterService;

    /*
     * 🐱method yaml: String[]
     * 🐱param steps: DeploymentResourceYamlRequest
     *
     * Idempotent operation that, based on the steps provided,
     * offer all the potential source code / custom resources to deploy the
     * integration.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/customResources")
    @Operation(summary = "Get CRDs",
            description = "Returns a list of all potential associated custom "
                    + "resource definitions."
                    + " This is an idempotent operation.")
    public Map<String, String> customResourcesDefinition(
            final @RequestBody DeploymentResourceYamlRequest request) {
        return deploymentService.crd(request.getName(), request.getSteps());
    }

    /*
     * 🐱method yaml: String[]
     * 🐱param steps: DeploymentResourceYamlRequest
     * 🐱param type: String
     *
     * Idempotent operation that, based on the steps provided,
     * offer the potential source code / custom resource to deploy the
     * integration. Tries to return the type of integration passed as
     * parameter. If that doesn't exist, it will default to the one defined
     * on the configuration. If that doesn't exist, it will just return one
     * randomly from the available ones.
     *
     * The usage of /customResources is encouraged over this one.
     *
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/yaml")
    @Path("/customResource")
    @Operation(summary = "Get CRD",
            description = "Returns the associated custom "
                    + "resource definition."
                    + " This is an idempotent operation.")
    public String customResourceDefinition(
            final @RequestBody DeploymentResourceYamlRequest request,
            final @QueryParam("type") String type) {
        Map<String, String> crds = customResourcesDefinition(request);

        if (crds.containsKey(type)) {
            return crds.get(type);
        }

        if (crds.containsKey(crdDefault)) {
            return crds.get(crdDefault);
        }

        return crds.values().iterator().next();
    }

    /*
     * 🐱method start: String
     * 🐱param steps: DeploymentResourceYamlRequest
     *
     * Deploys an integration provided.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/yaml")
    @Path("/")
    @Operation(summary = "Start integration",
            description = "Deploy and start the given integration"
                    + " on the cluster. Deployment will be done "
                    + "as a custom resource.")
    public String start(
            final @RequestBody DeploymentResourceYamlRequest request) {
        Map<String, String> yaml = deploymentService.crd(
                request.getName(), request.getSteps());
        final var crd = yaml.values().iterator().next();
        if (clusterService.start(crd)) {
            return crd;
        }
        return "Error deploying " + request.getName();
    }

    /*
     * 🐱method integrations: String
     *
     * Returns the list of all integrations
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    @Operation(summary = "Get all Integrations",
            description = "Returns all the integrations on the cluster.")
    public List<Integration> integrations() {
        return clusterService.getIntegrations();
    }


    /*
     * 🐱method stop: String
     *
     * Stops and deletes an integration by name
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{name}")
    @Operation(summary = "Stop/Remove integration",
            description = "Remove the integration identified by name.")
    public boolean integrations(
            final @Parameter(description = "Name of the integration to stop.")
            @PathParam("name") String name) {
        Integration i = new Integration();
        i.setName(name);
        return clusterService.stop(i);
    }

    @ServerExceptionMapper
    public Response mapException(final Exception x) {
        log.error("Error processing deployment.", x);

        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Error processing deployment: " + x.getMessage())
                .type(MediaType.TEXT_PLAIN_TYPE)
                .build();
    }

}
