package company.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Slf4j
@RestController
@EnableAutoConfiguration
@SpringBootApplication
@Api(tags = "svn-service")
@EnableSwagger2
public class SVNController {  
	private static final String APPLICATION_JSON_UTF8_VALUE = "application/json;charset=UTF-8";
    public static void main(String[] args)  {
		DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
        SpringApplication.run(SVNController.class, args);
    }
       
    @ApiOperation(value = "Get svn server file list", produces = APPLICATION_JSON_UTF8_VALUE)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "get svn list success", response = Resource.class),
            @ApiResponse(code = 500, message = "get svn list fail")
    })
	@RequestMapping(value={"/list"},produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
    public ResponseEntity<String> getSvnFileList(
    		@ApiParam(value = "svn repository")
    		    @RequestParam(value = "targetUrl", required = true)String targetUrl, 
    	    @ApiParam(value = "svn folder")
    		    @RequestParam(value = "url", required = true)String url,
    		@ApiParam(value = "user name")
    		    @RequestParam(value = "name", required = true)String name, 
    		@ApiParam(value = "user password")
    		    @RequestParam(value = "password", required = true)String password) throws Exception{
    	SVNRepository repo;
    	try {
             repo = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(targetUrl));
        }catch (SVNException e) {
        	log.error("Can't find SVN repository for " + targetUrl);
        	throw new Exception("Can't find SVN repository for " + targetUrl); 
        }
    	
		ISVNAuthenticationManager authManager = 
                SVNWCUtil.createDefaultAuthenticationManager(name,password);
        repo.setAuthenticationManager(authManager);
        try {
            String relativeUrl = getRelativeUrl(url, targetUrl);
            SVNNodeKind nodeKind = repo.checkPath(relativeUrl, -1);
            String responseString = "";
            if (nodeKind == SVNNodeKind.DIR) {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Collection<SVNDirEntry> entries = repo.getDir(relativeUrl, -1, null, (Collection)null);
                responseString = entries.stream().map(entry->entry.getName()+" "+entry.getKind()).collect(Collectors.joining(" "));
                return ResponseEntity.status(HttpStatus.OK).body(responseString.trim());
            } else {
            	log.error(url + "is not a directory. Can't get file list.");
            	throw new Exception(url + "is not a directory. Can't get file list." );
            }
        } catch (SVNException e) {
        	log.error("GetSvnFileList " + url + " failed");
        	throw new Exception("GetSvnFileList " + url + " failed");
        }
    }

    private String getRelativeUrl(String url, String baseUrl) {
        String relativeUrl = url.replaceFirst(baseUrl, "");
        if (relativeUrl.startsWith("/")) {
            relativeUrl = relativeUrl.substring(1);
        }
        return relativeUrl;
    }
    
    @ApiOperation(value = "Svn get file content", produces = APPLICATION_JSON_UTF8_VALUE)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "get file content success", response = InputStreamResource.class),
            @ApiResponse(code = 500, message = "get file content fail")
    })
    @RequestMapping(value={"/content"},produces = APPLICATION_JSON_UTF8_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> getSvnFileContent(
        @ApiParam(value = "svn repository")
		    @RequestParam(value = "targetUrl", required = true)String targetUrl, 
	    @ApiParam(value = "svn folder")
		    @RequestParam(value = "url", required = true)String url,
		@ApiParam(value = "user name")
		    @RequestParam(value = "name", required = true)String name, 
		@ApiParam(value = "user password")
		    @RequestParam(value = "password", required = true)String password) throws Exception{
        SVNRepository repo;
        try {
            repo = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(targetUrl));
       }catch (SVNException e) {
       	throw new Exception("Can't find SVN repository for " + targetUrl);
       }
       ISVNAuthenticationManager authManager = 
                SVNWCUtil.createDefaultAuthenticationManager(name,password);
       repo.setAuthenticationManager(authManager);
        try {
            String relativeUrl = getRelativeUrl(url, targetUrl);
            SVNNodeKind nodeKind = repo.checkPath(relativeUrl, -1);
            if (nodeKind == SVNNodeKind.FILE) {
                SVNProperties fileProperties = new SVNProperties();
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                repo.getFile(relativeUrl, -1, fileProperties, stream);
                HttpHeaders headers = new HttpHeaders();
                headers.add("length", String.valueOf(stream.size()));
                headers.add("date",fileProperties.getStringValue(SVNProperty.COMMITTED_DATE));
                return ResponseEntity.status(HttpStatus.OK).headers(headers).body(stream.toByteArray());
            } else {
            	log.error(url + " is not a file");
                throw new Exception(url + " is not a file");
            }
        } catch (SVNException e) {
        	log.error("GetSvnFileContent " + url + " failed");
            throw new Exception("GetSvnFileContent " + url + " failed");
        }
    }
   
    // Exception handling
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception e) {
        return e.getMessage();
    }

}
