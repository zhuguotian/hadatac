package org.hadatac.console.controllers.annotator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;

import org.hadatac.entity.pojo.Credential;
import org.hadatac.console.controllers.AuthApplication;
import org.hadatac.console.controllers.annotator.routes;
import org.hadatac.console.http.ResumableUpload;
import org.hadatac.console.models.AssignOptionForm;
import org.hadatac.console.models.LabKeyLoginForm;
import org.hadatac.console.models.SysUser;
import org.hadatac.console.views.html.annotator.*;
import org.hadatac.console.views.html.triplestore.*;
import org.hadatac.data.loader.AnnotationWorker;
import org.hadatac.data.loader.CSVRecordFile;
import org.hadatac.data.loader.GeneratorChain;
import org.hadatac.data.loader.RecordFile;
import org.hadatac.data.loader.SpreadsheetRecordFile;
import org.hadatac.console.views.html.*;
import org.hadatac.entity.pojo.DataFile;
import org.hadatac.entity.pojo.Measurement;
import org.hadatac.entity.pojo.ObjectAccessSpec;
import org.hadatac.entity.pojo.ObjectCollection;
import org.hadatac.entity.pojo.User;
import org.hadatac.metadata.loader.LabkeyDataHandler;
import org.hadatac.metadata.loader.URIUtils;
import org.hadatac.utils.ConfigProp;
import org.hadatac.utils.Feedback;
import org.hadatac.utils.FileManager;
import org.hadatac.utils.NameSpace;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.labkey.remoteapi.CommandException;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import play.twirl.api.Html;
import play.data.Form;
import play.data.FormFactory;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Http.MultipartFormData.FilePart;


public class AutoAnnotator extends Controller {

    @Inject
    FormFactory formFactory;

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result index(String dir, String dest) {        
        final SysUser user = AuthApplication.getLocalUser(session());
        
    	String newDir = Paths.get(dir, dest).normalize().toString();
        
        List<DataFile> procFiles = null;
        List<DataFile> unprocFiles = null;
        List<String> studyURIs = new ArrayList<String>();

        String pathProc = ConfigProp.getPathProc();
        String pathUnproc = ConfigProp.getPathUnproc();
        
        List<String> folders = DataFile.findFolders(Paths.get(pathProc, newDir).toString(), true);
        if (!"/".equals(newDir)) {
            folders.add(0, "..");
        }

        if (user.isDataManager()) {            
        	procFiles = DataFile.findInDir(newDir, DataFile.PROCESSED);
        	
            unprocFiles = DataFile.findInDir("", DataFile.UNPROCESSED);
            unprocFiles.addAll(DataFile.findInDir("", DataFile.FREEZED));
            
            String basePath = newDir;
            if (basePath.startsWith("/")) {
                basePath = basePath.substring(1, basePath.length());
            }
            
            DataFile.includeUnrecognizedFiles(Paths.get(pathProc, newDir).toString(), 
                    basePath, procFiles, user.getEmail(), DataFile.PROCESSED);
            
        	DataFile.includeUnrecognizedFiles(pathUnproc, "", 
        	        unprocFiles, user.getEmail(), DataFile.UNPROCESSED);
        } else {
            procFiles = DataFile.findInDir(newDir, user.getEmail(), DataFile.PROCESSED);
            
            unprocFiles = DataFile.findInDir("", user.getEmail(), DataFile.UNPROCESSED);
            unprocFiles.addAll(DataFile.findInDir("", user.getEmail(), DataFile.FREEZED));
        }

        DataFile.filterNonexistedFiles(pathProc, procFiles);
        DataFile.filterNonexistedFiles(pathUnproc, unprocFiles);

        for (DataFile dataFile : procFiles) {
            if (!dataFile.getStudyUri().isEmpty() && !studyURIs.contains(dataFile.getStudyUri())) {
                studyURIs.add(dataFile.getStudyUri());
            }
        }

        studyURIs.sort(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });

        boolean bStarted = false;
        if (ConfigProp.getPropertyValue("autoccsv.config", "auto").equals("on")) {
            bStarted = true;
        }
        
        unprocFiles.sort(new Comparator<DataFile>() {
            @Override
            public int compare(DataFile d1, DataFile d2) {
                return d1.getFileName().compareTo(d2.getFileName());
            }
        });

        procFiles.sort(new Comparator<DataFile>() {
            @Override
            public int compare(DataFile d1, DataFile d2) {
                return d1.getFileName().compareTo(d2.getFileName());
            }
        });

        return ok(autoAnnotator.render(newDir, folders, unprocFiles, procFiles, studyURIs, bStarted, user.isDataManager()));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result postIndex(String dir, String dest) {
        return index(dir, dest);
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result assignFileOwner(String dir, String ownerEmail, String fileId) {
        final SysUser user = AuthApplication.getLocalUser(session());
        
        DataFile dataFile = null;
        if (user.isDataManager()) {
            dataFile = DataFile.findById(fileId);
        } else {
            dataFile = DataFile.findByIdAndEmail(fileId, user.getEmail());
        }

        if (null == dataFile) {
            return badRequest("You do NOT have the permission to operate this file!");
        }
        
        return ok(assignOption.render(User.getUserEmails(),
                routes.AutoAnnotator.processOwnerForm(dir, ownerEmail, fileId),
                "Owner", 
                "Selected File", 
                dataFile.getFileName()));
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result postAssignFileOwner(String dir, String ownerEmail, String fileId) {
        return assignFileOwner(dir, ownerEmail, fileId);
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result processOwnerForm(String dir, String ownerEmail, String fileId) {
        Form<AssignOptionForm> form = formFactory.form(AssignOptionForm.class).bindFromRequest();
        AssignOptionForm data = form.get();

        if (form.hasErrors()) {
            System.out.println("HAS ERRORS");
            return badRequest(assignOption.render(User.getUserEmails(),
                    routes.AutoAnnotator.processOwnerForm(dir, ownerEmail, fileId),
                    "Owner",
                    "Selected File",
                    fileId));
        } else {
            DataFile file = DataFile.findByIdAndEmail(fileId, ownerEmail);
            if (file == null) {
                file = new DataFile(fileId);
                file.setOwnerEmail(AuthApplication.getLocalUser(session()).getEmail());
                file.setStatus(DataFile.UNPROCESSED);
                file.setSubmissionTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
            }
            file.setOwnerEmail(data.getOption());
            file.save();
            return redirect(routes.AutoAnnotator.index(dir, "."));
        }
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result assignDataAcquisition(String dir, String dataAcquisitionUri, String fileId) {
        final SysUser user = AuthApplication.getLocalUser(session());
        
        DataFile dataFile = null;
        if (user.isDataManager()) {
            dataFile = DataFile.findById(fileId);
        } else {
            dataFile = DataFile.findByIdAndEmail(fileId, user.getEmail());
        }

        if (null == dataFile) {
            return badRequest("You do NOT have the permission to operate this file!");
        }
        
        List<String> dataAcquisitionURIs = new ArrayList<String>();
        ObjectAccessSpec.findAll().forEach((da) -> dataAcquisitionURIs.add(
                URIUtils.replaceNameSpaceEx(da.getUri())));

        return ok(assignOption.render(dataAcquisitionURIs,
                routes.AutoAnnotator.processDataAcquisitionForm(dir, dataAcquisitionUri, fileId),
                "Object Access Specification",
                "Selected File",
                dataFile.getFileName()));
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result postAssignDataAcquisition(String dir, String dataAcquisitionUri, String fileId) {
        return assignDataAcquisition(dir, dataAcquisitionUri, fileId);
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result processDataAcquisitionForm(String dir, String dataAcquisitionUri, String fileId) {
        Form<AssignOptionForm> form = formFactory.form(AssignOptionForm.class).bindFromRequest();
        AssignOptionForm data = form.get();

        List<String> dataAcquisitionURIs = new ArrayList<String>();
        ObjectAccessSpec.findAll().forEach((da) -> dataAcquisitionURIs.add(
                URIUtils.replaceNameSpaceEx(da.getUri())));

        if (form.hasErrors()) {
            System.out.println("HAS ERRORS");
            return badRequest(assignOption.render(dataAcquisitionURIs,
                    routes.AutoAnnotator.processDataAcquisitionForm(dir, dataAcquisitionUri, fileId),
                    "Object Access Specification",
                    "Selected File",
                    fileId));
        } else {
            DataFile file = DataFile.findById(fileId);
            if (file == null) {
                file = new DataFile("Unknown_File.csv");
                file.setOwnerEmail(AuthApplication.getLocalUser(session()).getEmail());
                file.setStatus(DataFile.UNPROCESSED);
                file.setSubmissionTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
            }
            file.setDataAcquisitionUri(URIUtils.replacePrefixEx(data.getOption()));
            file.save();
            return redirect(routes.AutoAnnotator.index(dir, "."));
        }
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result toggleAutoAnnotator(String dir) {
        if (ConfigProp.getPropertyValue("autoccsv.config", "auto").equals("on")) {
            ConfigProp.setPropertyValue("autoccsv.config", "auto", "off");
            System.out.println("Turning auto-annotation off");
        }
        else {
            ConfigProp.setPropertyValue("autoccsv.config", "auto", "on");
            System.out.println("Turning auto-annotation on");
        }

        return redirect(routes.AutoAnnotator.index(dir, "."));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result downloadTemplates(String dir) {
        return ok(download_templates.render(dir));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result postDownloadTemplates(String dir) {
        return postDownloadTemplates(dir);
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result setLabKeyCredentials() {
        return ok(syncLabkey.render("init", routes.AutoAnnotator.
                postSetLabKeyCredentials().url(), ""));
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result postSetLabKeyCredentials() {
        Form<LabKeyLoginForm> form = formFactory.form(LabKeyLoginForm.class).bindFromRequest();
        String user_name = form.get().getUserName();
        String password = form.get().getPassword();
        LabkeyDataHandler loader = new LabkeyDataHandler(
                ConfigProp.getLabKeySite(), "/", user_name, password);
        try {
            loader.checkAuthentication();
            Credential cred = new Credential();
            cred.setUserName(user_name);
            cred.setPassword(password);
            cred.save();
        } catch(CommandException e) {
            if("Unauthorized".equals(e.getMessage())){
                return ok(syncLabkey.render("login_failed", "", ""));
            }
        }

        return ok(main.render("Results", "", 
                new Html("<h3>Your provided credentials are valid and saved!</h3>")));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result checkAnnotationLog(String dir, String fileId) {
    	if (fileId == null) {
    		fileId = "";
    	}
    	if (DataFile.findById(fileId) == null || 
    		DataFile.findById(fileId).getLogger() == null) {
            return ok(annotation_log.render(Feedback.print(Feedback.WEB, ""), 
                    routes.AutoAnnotator.index(dir, ".").url()));
    	}
        return ok(annotation_log.render(Feedback.print(Feedback.WEB, 
                DataFile.findById(fileId).getLogger().getLog()), 
                routes.AutoAnnotator.index(dir, ".").url()));
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result checkErrorDictionary() {
        InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("error_dictionary.json");
        String jsonText = "";
        try {
            jsonText = IOUtils.toString(inputStream, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return ok(error_dictionary.render(jsonText, routes.AutoAnnotator.index("/", ".").url()));
    }

    public Result getAnnotationStatus(String fileId) {
        DataFile dataFile = DataFile.findById(fileId);
        Map<String, Object> result = new HashMap<String, Object>();

        if (dataFile == null) {
            result.put("File Id", fileId);
            result.put("Status", "Unknown");
            result.put("Error", "The file with the specified id cannot be retrieved. "
                    + "Please provide a valid file id.");
        } else {
            result.put("File Name", dataFile.getFileName());
            result.put("Status", dataFile.getStatus());
            result.put("Submission Time", dataFile.getSubmissionTime());
            result.put("Completion Time", dataFile.getCompletionTime());
            result.put("Owner Email", dataFile.getOwnerEmail());
            result.put("Log", dataFile.getLog());
        }

        return ok(Json.toJson(result));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result moveDataFile(String dir, String fileId) {        
        final SysUser user = AuthApplication.getLocalUser(session());
        
        DataFile dataFile = null;
        if (user.isDataManager()) {
            dataFile = DataFile.findById(fileId);
        } else {
            dataFile = DataFile.findByIdAndEmail(fileId, user.getEmail());
        }

        if (null == dataFile) {
            return badRequest("You do NOT have the permission to operate this file!");
        }

        String pathProc = ConfigProp.getPathProc();
        String pathUnproc = ConfigProp.getPathUnproc();
        File file = new File(dataFile.getAbsolutePath());

        if (dataFile.getPureFileName().startsWith("DA-")) {
            Measurement.deleteFromSolr(dataFile.getDatasetUri());
            NameSpace.deleteTriplesByNamedGraph(URIUtils.replacePrefixEx(dataFile.getDataAcquisitionUri()));
        } else {
            deleteAddedTriples(file, dataFile);
        }

        dataFile.resetForUnprocessed();
        
        File destFolder = new File(pathUnproc);
        if (!destFolder.exists()){
            destFolder.mkdirs();
        }
        file.renameTo(new File(destFolder + "/" + dataFile.getPureFileName()));
        file.delete();

        dataFile.getLogger().addLine(Feedback.println(Feedback.WEB,
                String.format("[OK] Moved file %s to unprocessed folder", dataFile.getPureFileName())));
        dataFile.save();

        return redirect(routes.AutoAnnotator.index(dir, "."));
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result moveDataFileToWorking(String fileId) {
        final SysUser user = AuthApplication.getLocalUser(session());
        
        DataFile dataFile = null;
        if (user.isDataManager()) {
            dataFile = DataFile.findByIdAndStatus(fileId, DataFile.UNPROCESSED);
        } else {
            dataFile = DataFile.findByIdAndOwnerEmailAndStatus(
                    fileId, user.getEmail(), DataFile.UNPROCESSED);
        }

        if (null == dataFile) {
            return badRequest("You do NOT have the permission to operate this file!");
        }
        
        if (dataFile.existsInFileSystem(ConfigProp.getPathWorking())) {
            return badRequest("<a style=\"color:#cc3300; font-size: x-large;\">A file with this name already exists!</a>");
        }
        
        File file = new File(dataFile.getAbsolutePath());
        File destFolder = new File(ConfigProp.getPathWorking());
        if (!destFolder.exists()) {
            destFolder.mkdirs();
        }
        file.renameTo(new File(destFolder.getPath() + "/" + dataFile.getPureFileName()));
        file.delete();
        
        dataFile.getLogger().resetLog();
        dataFile.setDir("");
        dataFile.setStatus(DataFile.WORKING);
        dataFile.setSubmissionTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
        dataFile.save();
        
        return redirect(routes.AutoAnnotator.index("/", "."));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result activateDataFile(String dir, String fileId) {           
        final SysUser user = AuthApplication.getLocalUser(session());
        DataFile dataFile = null;
        if (user.isDataManager()) {
            dataFile = DataFile.findById(fileId);
        } else {
            dataFile = DataFile.findByIdAndEmail(fileId, user.getEmail());
        }
        
        if (null == dataFile) {
            return badRequest("You do NOT have the permission to operate this file!");
        }

        dataFile.setStatus(DataFile.UNPROCESSED);
        dataFile.getLogger().resetLog();
        dataFile.save();

        return redirect(routes.AutoAnnotator.index(dir, "."));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result deleteDataFile(String dir, String fileId) {
        final SysUser user = AuthApplication.getLocalUser(session());
        
        DataFile dataFile = null;
        if (user.isDataManager()) {
            dataFile = DataFile.findById(fileId);
        } else {
            dataFile = DataFile.findByIdAndEmail(fileId, user.getEmail());
        }
        
        if (null == dataFile) {
            return badRequest("You do NOT have the permission to operate this file!");
        }

        File file = new File(dataFile.getAbsolutePath());

        if (dataFile.getPureFileName().startsWith("DA-")) {
            Measurement.deleteFromSolr(dataFile.getDatasetUri());
            NameSpace.deleteTriplesByNamedGraph(URIUtils.replacePrefixEx(dataFile.getDataAcquisitionUri()));
        } else {
            try {
                deleteAddedTriples(file, dataFile);
            } catch (Exception e) {
                System.out.print("Can not delete triples ingested by " + dataFile.getFileName() + " ..");
                file.delete();
                dataFile.delete();
                
                return redirect(routes.AutoAnnotator.index(dir, "."));
            }
        }
        file.delete();
        dataFile.delete();

        return redirect(routes.AutoAnnotator.index(dir, "."));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static void deleteAddedTriples(File file, DataFile dataFile) {
        System.out.println("Deleting the added triples from the moving file ...");

        RecordFile recordFile = null;
        if (file.getName().endsWith(".csv")) {
            recordFile = new CSVRecordFile(file);
        } else if (file.getName().endsWith(".xlsx")) {
            recordFile = new SpreadsheetRecordFile(file);
        } else {
            dataFile.getLogger().addLine(Feedback.println(Feedback.WEB, String.format(
                    "[ERROR] Unknown file format: %s", file.getName())));
            return;
        }
        
        dataFile.setRecordFile(recordFile);
        GeneratorChain chain = AnnotationWorker.getGeneratorChain(dataFile);
        
        if (chain != null) {
            chain.delete();
        }
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result downloadDataFile(String fileId) {
        final SysUser user = AuthApplication.getLocalUser(session());
        DataFile dataFile = DataFile.findByIdAndEmail(fileId, user.getEmail());
        if (null == dataFile) {
            return badRequest("You do NOT have the permission to download this file!");
        }
        
        return ok(new File(dataFile.getAbsolutePath()));
    }
    
    // access to media files does no require ownership verification
    public Result downloadMediaFile(String mediaFileName) {
        //System.out.println("MediaFile: " + Paths.get(ConfigProp.getPathProc(), "media", mediaFileName.replace("file://", "")).toString()); 
        return ok(new File(Paths.get(ConfigProp.getPathProc(), "media", mediaFileName.replace("file://", "")).toString())); 
    }
    
    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result deleteFolder(String dir) {	
        List<DataFile> dfs = DataFile.findInDir(dir,DataFile.PROCESSED);
        int totFiles;
        if (dfs == null) {
        	totFiles = 0;
        } else {
        	totFiles = dfs.size();
        }
        
        List<String> folders = DataFile.findFolders(dir, false);
        boolean noSubFolders = (folders.size() == 0 || (folders.size() == 1 && folders.get(0) != null && folders.get(0).equals("..")));
        String statusMsg;
        if (noSubFolders) {
        	statusMsg = "Folder can be deleted because it has no sub-folders.";
        } else {
        	statusMsg = "Folder cannot be deleted becuase it has sub-folders.";
        }
        
    	return ok(deleteFolder.render(dir, !noSubFolders, totFiles, statusMsg));
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result postDeleteFolder(String dir) {
        return deleteFolder(dir);
    }

    @Restrict(@Group(AuthApplication.DATA_MANAGER_ROLE))
    public Result processDeleteFolder(String dir) {
        List<DataFile> dfs = DataFile.findInDir(dir, DataFile.PROCESSED);
        for (DataFile df : dfs) {
            File file = new File(df.getAbsolutePath());
            System.out.println(df.getAbsolutePath() + "  " + df.getPureFileName());
            
            if (df.getPureFileName().startsWith("DA-")) {
                Measurement.deleteFromSolr(df.getDatasetUri());
                NameSpace.deleteTriplesByNamedGraph(URIUtils.replacePrefixEx(df.getDataAcquisitionUri()));
            } else {
                try {
                    deleteAddedTriples(file, df);
                } catch (Exception e) {
                    System.out.print("Can not delete triples ingested by " + df.getFileName() + " ..");
                    file.delete();
                    df.delete();
                    
                    return redirect(routes.AutoAnnotator.index(dir, "."));
                }
            }
            file.delete();
            df.delete();
        }
        DataFile folder = new DataFile(dir);
        File folderFile = new File(folder.getAbsolutePath());
		System.out.println(folder.getFileName());
		System.out.println(folderFile.getAbsolutePath() + "  " + folderFile.getName());
        if (folderFile.exists()) {
        	try { 
        		folderFile.delete();
        		folder.delete();
            } catch (Exception e) {
                System.out.print("Can not delete folder " + dir + " itself");
                return redirect(routes.AutoAnnotator.index(dir, "."));
            }
        }
        return redirect(routes.AutoAnnotator.index(dir, ".."));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result saveDataFile() {        
        FilePart uploadedfile = request().body().asMultipartFormData().getFile("file");
        
        JSONParser parser = new JSONParser();
        JSONObject params = null;
        try {
            String metadata = URLDecoder.decode(uploadedfile.getFilename(), "utf-8");
            params = (JSONObject)parser.parse(metadata);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        String fileId = (String)params.get("fileId");
        
        if (uploadedfile != null) {
            final SysUser user = AuthApplication.getLocalUser(session());
            DataFile dataFile = DataFile.findByIdAndEmail(fileId, user.getEmail());
            if (null == dataFile) {
                return ok("<a style=\"color:#cc3300; font-size: large;\">This file can be modified only by its owner!</a>");
            }
            
            File file = new File(dataFile.getAbsolutePath());
            if (!file.exists()) {
                return ok("<a style=\"color:#cc3300; font-size: large;\">Could not find this file on records!</a>");
            }
            
            InputStream fileInputStream;
            try {
                fileInputStream = new FileInputStream((File)uploadedfile.getFile());
                byte[] byteFile = IOUtils.toByteArray(fileInputStream);
                FileUtils.writeByteArrayToFile(file, byteFile);
                fileInputStream.close();
            } catch (Exception e) {
                return ok("<a style=\"color:#cc3300; font-size: large;\">Error uploading file. Please try again.</a>");
            }
            
            return ok("<a style=\"color:#008000; font-size: large;\">File successfully saved!</a>");
        } else {
            return ok("<a style=\"color:#cc3300; font-size: large;\">Error uploading file. Please try again.</a>");
        }
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result uploadDataFileByChunking(
            String resumableChunkNumber,
            String resumableChunkSize, 
            String resumableCurrentChunkSize,
            String resumableTotalSize,
            String resumableType,
            String resumableIdentifier,
            String resumableFilename,
            String resumableRelativePath) {
        if (ResumableUpload.uploadFileByChunking(request(), 
                ConfigProp.getPathUnproc())) {
            //This Chunk has been Uploaded.
            return ok("Uploaded.");
        } else {
            return status(HttpStatus.SC_NOT_FOUND);
        }
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result postUploadDataFileByChunking(
            String resumableChunkNumber, 
            String resumableChunkSize, 
            String resumableCurrentChunkSize,
            String resumableTotalSize,
            String resumableType,
            String resumableIdentifier,
            String resumableFilename,
            String resumableRelativePath) {

        Path path = Paths.get(resumableFilename);
        if (path == null) {
            return badRequest("<a style=\"color:#cc3300; font-size: x-large;\">Could not get file path!</a>");
        }

        String fileName = path.getFileName().toString();
        DataFile file = DataFile.findByNameAndStatus(fileName, DataFile.UNPROCESSED);
        if (file != null && file.existsInFileSystem(ConfigProp.getPathUnproc())) {
            return badRequest("<a style=\"color:#cc3300; font-size: x-large;\">A file with this name already exists!</a>");
        }

        if (ResumableUpload.postUploadFileByChunking(request(), ConfigProp.getPathUnproc())) {
            DataFile.create(fileName, "", AuthApplication.getLocalUser(session()).getEmail(), DataFile.UNPROCESSED);
            return(ok("Upload finished"));
        } else {
            return(ok("Upload"));
        }
    }
}

