package com.example.googleapis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;


@Controller
public class PhotosUIController {

    @Value("${photolibrary.authorizer}")
    private String authorizer;

    @Value("${photolibrary.albums.uri}")
    private String getAlbums;

    @Value("${photolibrary.photos.uri}")
    private String getPhotos;

    @Value("${photolibrary.logout.url}")
    private String logout;

    @Autowired
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    // Display the home page of the application
    @GetMapping ("/")
    ModelAndView showWelcomePage(OAuth2AuthenticationToken authenticate) {

        OAuth2User principal = authenticate.getPrincipal();

        System.out.println("Get the attys: ");
        System.out.println(principal.getAttributes());

        ModelAndView modelAndView = getUserModel(principal);
        modelAndView.setViewName("welcome");
        return modelAndView;
    }

    /*
     * When the user requests to see all photos of an album, then a call
     * is made to the Resource server to get the information.
     */
    @GetMapping("/photolibrary/pics")
    ModelAndView retrievePhotos(HttpServletRequest request, OAuth2AuthenticationToken authn) {

        String albumId = request.getParameter("id");
        System.out.println(">>>>>>>>>> Retrieving photos from " + albumId);

        OAuth2User principal = authn.getPrincipal();
        // System.out.println(principal.getAttributes());

        // Get the Authorized cli .. Very important object
        OAuth2AuthorizedClient client = oAuth2AuthorizedClientService.loadAuthorizedClient(authn.getAuthorizedClientRegistrationId(),
                authn.getName());

        // I'm a very visual person. I'd like to see what the token and name look like
        System.out.println(">>>>>>>>>> Authentication Token: " + authn.getAuthorizedClientRegistrationId());
        System.out.println(">>>>>>>>>> Authentication Token: " + authn.getName());

        if (client == null) {
            return destroySessionAndRedirectToHome(request);
        }

        System.out.println(">>>>>>>>>> Client token and its value: " + client.getAccessToken().getTokenValue());

        // Call the resource server
        // RestTemplate used to send a request to an API
        RestTemplate restTemplate = new RestTemplate();

        // Http Header object used to customize the header in the soon to be created http entity
        HttpHeaders headers = new HttpHeaders();

        // Specifically we are adding an authoriztion type (bearer) and content-type (application/json)
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");

        // The request body string will be used to pass the albumid to the google api responsible
        // for getting the specified album
        String reqBody = String.format("""
				{
					"albumId": "%1s",
					"pageSize": "%2s"
				}
				""", albumId,"100");
        HttpEntity entity = new HttpEntity(reqBody, headers);

        // The response will hold the information we get back from exchanging the information in the HttpEntity with
        // the information the google api provides
        ResponseEntity<Map> response = restTemplate.exchange(this.getPhotos, HttpMethod.POST, entity, Map.class);

        System.out.println(">>>>>>>>>> The response from Google: " + response.getBody());
        System.out.println(">>>>>>>>>> The headers from response body: " + response.getHeaders());
        System.out.println(">>>>>>>>>> The status code from response body: " + response.getStatusCode());

        // List of photos are obtained from the resource server
        List<?> photos = (List<?>) response.getBody().get("mediaItems");

        // delegates to the view html file for display
        // photos-listing.html
        ModelAndView model = getUserModel(principal);
        model.addObject("photos", photos);
        model.setViewName("photos-listing");
        return model;
    }

    /*
     * When the user requests to see all albums, then a call is made to the
     * Resource server to get the information.
     */
    @GetMapping("/photolibrary/albums")
    ModelAndView retrieveAlbums(HttpServletRequest request, OAuth2AuthenticationToken authn) {

        OAuth2User principal = authn.getPrincipal();
        // System.out.println(principal.getAttributes());

        // Get the Authorized cli .. Very important object
        OAuth2AuthorizedClient client = oAuth2AuthorizedClientService.loadAuthorizedClient(authn.getAuthorizedClientRegistrationId(),
                authn.getName());

        System.out.println(">>> " + authn.getAuthorizedClientRegistrationId());
        System.out.println(">>> " + authn.getName());

        if (client == null) {
            return destroySessionAndRedirectToHome(request);
        }

        // System.out.println(client.getAccessToken().getTokenValue());

        // Call the resource server
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
        HttpEntity entity = new HttpEntity("", headers);
        ResponseEntity<Map> response = restTemplate.exchange(this.getAlbums, HttpMethod.GET, entity, Map.class);
        System.out.println(response.getBody());

        // Returns a list of Albums
        List<?> albums = (List<?>) response.getBody().get("albums");

        // delegate to the view object for display
        ModelAndView model = getUserModel(principal);
        model.addObject("albums", albums);
        model.setViewName("album-listing");
        return model;
    }

    // Send some basic user and setup information to the views
    // by default. The call can add more properties to it before passing to the view file
    private ModelAndView getUserModel(OAuth2User principal) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("authorizer", this.authorizer);
        modelAndView.addObject("firstName", principal.getAttribute("given_name"));
        modelAndView.addObject("lastName", principal.getAttribute("family_name"));
        modelAndView.addObject("email", principal.getAttribute("email"));

        // If no picture is retrieved, then set the default picture
        String pic = principal.getAttribute("picture");
        if (pic == null) {
            pic = "/static/images/person.svg";
        }

        modelAndView.addObject("picture", pic);
        return modelAndView;
    }

    /*
     * If you restart the "client", then next request from UI needs to be redirected
     * to the home page. The Authorization Server session could still be valid and
     * so, you may not be asked for the credentials. That's okay.
     */
    private ModelAndView destroySessionAndRedirectToHome(HttpServletRequest request) {

        // invalidate the session
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
            System.out.println("> Destroying session. Next login, the request will go to Authorization Server");
        }

        // redirect to the main page
        return new ModelAndView("redirect:http://localhost:8080/");
    }

    /*
     * Destroy the session and redirect to the authorization server
     * logout endpoint. This is done so that all sessions are desroyed.
     */
    @GetMapping("/photolibrary/logout")
    ModelAndView logout(@AuthenticationPrincipal OidcUser user, HttpServletRequest request, OAuth2AuthenticationToken authn) {

        // invalidate the session
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
            System.out.println("> Destroying session. Next login, the request will go to Authorization Server");
        }

        // global logout. The session in Authorization and Authentication
        // Server will be terminated
        System.out.println("> Redirect to the authorization server for a global logout");
        return new ModelAndView("redirect:" + logout + "?id_token_hint=" + user.getIdToken().getTokenValue());
    }
}
