package org.apache.axis2.deployment.util;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.deployment.DeploymentConstants;
import org.apache.axis2.deployment.DeploymentException;
import org.apache.axis2.deployment.DeploymentEngine;
import org.apache.axis2.deployment.ServiceBuilder;
import org.apache.axis2.deployment.repository.util.ArchiveFileData;
import org.apache.axis2.deployment.repository.util.ArchiveReader;
import org.apache.axis2.description.*;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.engine.DependencyManager;
import org.apache.axis2.engine.Handler;
import org.apache.axis2.engine.MessageReceiver;
import org.apache.axis2.wsdl.WSDLConstants;
import org.apache.ws.java2wsdl.Java2WSDLConstants;
import org.apache.ws.java2wsdl.SchemaGenerator;
import org.apache.ws.java2wsdl.utils.TypeTable;
import org.codehaus.jam.JMethod;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/*
* Copyright 2004,2005 The Apache Software Foundation.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/

public class Utils {
    public static void addFlowHandlers(Flow flow, ClassLoader clsLoader) throws AxisFault {
        int count = flow.getHandlerCount();

        for (int j = 0; j < count; j++) {
            HandlerDescription handlermd = flow.getHandler(j);
            Class handlerClass;
            Handler handler;

            handlerClass = getHandlerClass(handlermd.getClassName(), clsLoader);

            try {
                handler = (Handler) handlerClass.newInstance();
                handler.init(handlermd);
                handlermd.setHandler(handler);
            } catch (InstantiationException e) {
                throw new AxisFault(e);
            } catch (IllegalAccessException e) {
                throw new AxisFault(e);
            }
        }
    }

    public static void loadHandler(ClassLoader loader1, HandlerDescription desc)
            throws DeploymentException {
        String handlername = desc.getClassName();
        Handler handler;
        Class handlerClass;

        try {
            handlerClass = Class.forName(handlername, true, loader1);
            handler = (Handler) handlerClass.newInstance();
            handler.init(desc);
            desc.setHandler(handler);
        } catch (ClassNotFoundException e) {
            throw new DeploymentException(e);
        } catch (Exception e) {
            throw new DeploymentException(e);
        }
    }

    public static URL[] getURLsForAllJars(URL url, boolean antiJARLocking) {
        try {
            ArrayList array = new ArrayList();
            String urlString = url.toString();
            InputStream in = url.openStream();
            ZipInputStream zin ;
            if (antiJARLocking) {
                File inputFile = createTempFile(urlString.substring(urlString.length() - 4), in);
                in.close();
                array.add(inputFile.toURL());
                zin = new ZipInputStream(new FileInputStream(inputFile));
            } else {
                array.add(url);
                zin = new ZipInputStream(in);
            }
            ZipEntry entry;
            String entryName;
            while ((entry = zin.getNextEntry()) != null) {
                entryName = entry.getName();
                /**
                 * id the entry name start with /lib and end with .jar
                 * then those entry name will be added to the arraylist
                 */
                if ((entryName != null) && entryName.toLowerCase().startsWith("lib/")
                        && entryName.toLowerCase().endsWith(".jar")) {
                    String suffix = entryName.substring(4);
                    File f = createTempFile(suffix, zin);
                    array.add(f.toURL());
                }
            }
            zin.close();
            return (URL[]) array.toArray(new URL[array.size()]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static File createTempFile(String suffix, InputStream in) throws IOException {
        byte data[] = new byte[2048];
        int count;
        new File(System.getProperty("java.io.tmpdir")).mkdirs();
        File f = File.createTempFile("axis2", suffix);
        f.deleteOnExit();
        FileOutputStream out = new FileOutputStream(f);
        while ((count = in.read(data, 0, 2048)) != -1) {
            out.write(data, 0, count);
        }
        out.close();
        return f;
    }

    public static ClassLoader getClassLoader(ClassLoader parent, String path)
            throws DeploymentException {
        return getClassLoader(parent, new File(path));
    }

    public static ClassLoader getClassLoader(ClassLoader parent, File file)
            throws DeploymentException {
        URLClassLoader classLoader;

        if (file != null) {
            try {
                ArrayList urls = new ArrayList();
                urls.add(file.toURL());
                // lower case directory name
                File libfiles = new File(file, "lib");
                if (libfiles.exists()) {
                    urls.add(libfiles.toURL());
                    File jarfiles[] = libfiles.listFiles();
                    for (int i = 0; i < jarfiles.length; i++) {
                        File jarfile = jarfiles[i];
                        if (jarfile.getName().endsWith(".jar")) {
                            urls.add(jarfile.toURL());
                        }
                    }
                } else {
                    // upper case directory name
                    libfiles = new File(file, "Lib");
                    if (libfiles.exists()) {
                        urls.add(libfiles.toURL());
                        File jarfiles[] = libfiles.listFiles();
                        for (int i = 0; i < jarfiles.length; i++) {
                            File jarfile = jarfiles[i];
                            if (jarfile.getName().endsWith(".jar")) {
                                urls.add(jarfile.toURL());
                            }
                        }
                    }
                }

                URL urllist[] = new URL[urls.size()];
                for (int i = 0; i < urls.size(); i++) {
                    urllist[i] = (URL) urls.get(i);
                }
                classLoader = new URLClassLoader(urllist, parent);
                return classLoader;
            } catch (MalformedURLException e) {
                throw new DeploymentException(e);
            }
        }

        return null;
    }

    private static Class getHandlerClass(String className, ClassLoader loader1) throws AxisFault {
        Class handlerClass;

        try {
            handlerClass = Class.forName(className, true, loader1);
        } catch (ClassNotFoundException e) {
            throw new AxisFault(e.getMessage());
        }

        return handlerClass;
    }

    /**
     * This guy will create a AxisService using java reflection
     */
    public static void fillAxisService(AxisService axisService,
                                       AxisConfiguration axisConfig, ArrayList excludeOperations) throws Exception {
        Parameter implInfoParam = axisService.getParameter(Constants.SERVICE_CLASS);
        if (implInfoParam == null) {
            // Nothing to do.
            return;
        }
        // adding name spaces
        Map map = new HashMap();
        map.put(Java2WSDLConstants.AXIS2_NAMESPACE_PREFIX,
                Java2WSDLConstants.AXIS2_XSD);
        map.put(Java2WSDLConstants.DEFAULT_SCHEMA_NAMESPACE_PREFIX,
                Java2WSDLConstants.URI_2001_SCHEMA_XSD);
        axisService.setNameSpacesMap(map);
        String serviceClass = (String) implInfoParam.getValue();
        ClassLoader serviceClassLoader = axisService.getClassLoader();
        SchemaGenerator schemaGenerator = new SchemaGenerator(serviceClassLoader,
                serviceClass.trim(), axisService.getSchematargetNamespace(),
                axisService.getSchematargetNamespacePrefix());
        schemaGenerator.setExcludeMethods(excludeOperations);
        if (!axisService.isElementFormDefault()) {
            schemaGenerator.setElementFormDefault(Java2WSDLConstants.FORM_DEFAULT_UNQUALIFIED);
        }
        Collection schemas = schemaGenerator.generateSchema();
        axisService.addSchema(schemas);
        axisService.setSchematargetNamespace(schemaGenerator.getSchemaTargetNameSpace());

        JMethod [] method = schemaGenerator.getMethods();
        TypeTable table = schemaGenerator.getTypeTable();
        PhasesInfo pinfo = axisConfig.getPhasesInfo();


        for (int i = 0; i < method.length; i++) {
            JMethod jmethod = method[i];
            if (!jmethod.isPublic()) {
                // no need to expose , private and protected methods
                continue;
            }
            if (jmethod.getSimpleName().equals("init")
                    || DependencyManager.MESSAGE_CONTEXT_INJECTION_METHOD.equals(jmethod.getSimpleName())
                    || DependencyManager.SERVICE_DESTROY_METHOD.equals(jmethod.getSimpleName()))
                continue;
            String opName = jmethod.getSimpleName();
            AxisOperation operation = axisService.getOperation(new QName(opName));
            // if the opeartion there in services.xml then try to set it schema element name
            if (operation != null) {
                AxisMessage inMessage = operation.getMessage(
                        WSDLConstants.MESSAGE_LABEL_IN_VALUE);
                if (inMessage != null) {
                    inMessage.setName(opName + Java2WSDLConstants.MESSAGE_SUFFIX);
                    inMessage.setElementQName(table.getComplexSchemaType(jmethod.getSimpleName()));
                }
                if (!jmethod.getReturnType().isVoidType()) {
                    AxisMessage outMessage = operation.getMessage(
                            WSDLConstants.MESSAGE_LABEL_OUT_VALUE);
                    outMessage.setElementQName(table.getQNamefortheType(jmethod.getSimpleName() +
                            Java2WSDLConstants.RESPONSE));
                    outMessage.setName(opName + Java2WSDLConstants.RESPONSE);
                }
            } else {
                operation = getAxisOperationforJmethod(jmethod, table);
                MessageReceiver mr = axisService.getMessageReceiver(
                        operation.getMessageExchangePattern());
                if (mr != null) {
                    operation.setMessageReceiver(mr);
                } else {
                    mr = axisConfig.getMessageReceiver(operation.getMessageExchangePattern());
                    operation.setMessageReceiver(mr);
                }
                pinfo.setOperationPhases(operation);
                axisService.addOperation(operation);
            }
            operation.setSoapAction("urn:" + opName);
        }
    }

    public static AxisOperation getAxisOperationforJmethod(JMethod jmethod,
                                                           TypeTable table) throws AxisFault {
        AxisOperation operation;
        String opName = jmethod.getSimpleName();
        if ("init".equals(opName)) {
            return null;
        }
        if (jmethod.getReturnType().isVoidType()) {
            operation = AxisOperationFactory.getAxisOperation(WSDLConstants.WSDL20_2004Constants.MEP_CONSTANT_IN_ONLY);
        } else {
            operation = AxisOperationFactory.getAxisOperation(WSDLConstants.WSDL20_2004Constants.MEP_CONSTANT_IN_OUT);
            AxisMessage outMessage = operation.getMessage(
                    WSDLConstants.MESSAGE_LABEL_OUT_VALUE);
            outMessage.setElementQName(table.getQNamefortheType(jmethod.getSimpleName() +
                    Java2WSDLConstants.RESPONSE));
            outMessage.setName(opName + Java2WSDLConstants.RESPONSE);
        }

        operation.setName(new QName(opName));
        AxisMessage inMessage = operation.getMessage(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
        if (inMessage != null) {
            inMessage.setElementQName(table.getComplexSchemaType(jmethod.getSimpleName()));
            inMessage.setName(opName + Java2WSDLConstants.MESSAGE_SUFFIX);
        }
        return operation;
    }

    public static OMElement getParameter(String name, String value, boolean locked) {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        OMElement parameter = fac.createOMElement("parameter", null);
        parameter.addAttribute("name", name, null);
        parameter.addAttribute("locked", Boolean.toString(locked), null);
        parameter.setText(value);
        return parameter;
    }

    /**
     * This method is to get the list of services there in a module
     * if module want to add services then the way of doing that is
     * 1. Add a foldere called services inside the module (both in mar case and expanded case)
     * 2. Then add a services.list file into that directory adding all the modules
     * you want to add
     * 3. Then put all the services into services directory in the module
     * 4. All the class is module can be access via a the module services.
     */

    public static void deployModuelServics(AxisModule module,
                                           AxisConfiguration axisConfig) throws AxisFault {
        try {
            ArchiveReader archiveReader = new ArchiveReader();
            PhasesInfo phasesInfo = axisConfig.getPhasesInfo();
            ClassLoader moduleClassLoader = module.getModuleClassLoader();
            ArrayList services = new ArrayList();
            InputStream in = moduleClassLoader.getResourceAsStream("aars/aars.list");
            if (in != null) {
                BufferedReader input;
                try {
                    input = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while ((line = input.readLine()) != null) {
                        services.add(line);
                    }
                    input.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (services.size() > 0) {
                for (int i = 0; i < services.size(); i++) {
                    String servicename = (String) services.get(i);
                    if (servicename == null || "".equals(servicename)) {
                        continue;
                    }
                    InputStream fin = moduleClassLoader.getResourceAsStream("aars/" + servicename);
                    if (fin == null) {
                        throw new AxisFault("No service archiev found : " + servicename);
                    }
                    File inputFile = Utils.createTempFile(servicename, fin);
                    ArchiveFileData filedata = new ArchiveFileData(inputFile, DeploymentConstants.TYPE_SERVICE, false);

                    filedata.setClassLoader(false,
                            moduleClassLoader);
                    HashMap wsdlservice = archiveReader.processWSDLs(filedata);
                    if (wsdlservice != null && wsdlservice.size() > 0) {
                        Iterator servicesitr = wsdlservice.values().iterator();
                        while (servicesitr.hasNext()) {
                            AxisService service = (AxisService) servicesitr.next();
                            Iterator operations = service.getOperations();
                            while (operations.hasNext()) {
                                AxisOperation axisOperation = (AxisOperation) operations.next();
                                phasesInfo.setOperationPhases(axisOperation);
                            }
                        }
                    }
                    AxisServiceGroup serviceGroup = new AxisServiceGroup(axisConfig);
                    serviceGroup.setServiceGroupClassLoader(
                            filedata.getClassLoader());
                    ArrayList serviceList = archiveReader.processServiceGroup(
                            filedata.getAbsolutePath(), filedata,
                            serviceGroup, false, wsdlservice,
                            axisConfig);
                    for (int j = 0; j < serviceList.size(); j++) {
                        AxisService axisService = (AxisService) serviceList.get(j);
                        serviceGroup.addService(axisService);
                    }
                    axisConfig.addServiceGroup(serviceGroup);
                    fin.close();
                }
            }
        } catch (IOException e) {
            throw new AxisFault(e);
        }
    }

    /**
     * Builds ModuleDescription for a given module archive file. This does not
     * called the init method since there is no refernce to configuration context
     * so who ever create module usieng this has to called module.init if it is
     * required
     *
     * @param modulearchive : Actual module archive file
     * @param config        : AxisConfiguration : for get classs loders etc..
     * @return
     * @throws org.apache.axis2.deployment.DeploymentException
     */
    public static AxisModule buildModule(File modulearchive, DeploymentEngine engine, AxisConfiguration config)
            throws DeploymentException {
        AxisModule axismodule;
        try {
            ArchiveFileData currentArchiveFile = new ArchiveFileData(modulearchive, DeploymentConstants.TYPE_MODULE, false);
            axismodule = new AxisModule();
            ArchiveReader archiveReader = new ArchiveReader();

            currentArchiveFile.setClassLoader(false, config.getModuleClassLoader());
            axismodule.setModuleClassLoader(currentArchiveFile.getClassLoader());
            archiveReader.readModuleArchive(currentArchiveFile, axismodule,
                    false, config);
            ClassLoader moduleClassLoader = axismodule.getModuleClassLoader();
            Flow inflow = axismodule.getInFlow();

            if (inflow != null) {
                Utils.addFlowHandlers(inflow, moduleClassLoader);
            }

            Flow outFlow = axismodule.getOutFlow();

            if (outFlow != null) {
                Utils.addFlowHandlers(outFlow, moduleClassLoader);
            }

            Flow faultInFlow = axismodule.getFaultInFlow();

            if (faultInFlow != null) {
                Utils.addFlowHandlers(faultInFlow, moduleClassLoader);
            }

            Flow faultOutFlow = axismodule.getFaultOutFlow();

            if (faultOutFlow != null) {
                Utils.addFlowHandlers(faultOutFlow, moduleClassLoader);
            }
        } catch (AxisFault axisFault) {
            throw new DeploymentException(axisFault);
        }
        return axismodule;
    }

    /**
     * Fills an axisservice object using services.xml. First creates
     * an axisservice object using WSDL and then fills it using the given services.xml.
     * Loads all the required class and builds the chains, finally adds the
     * servicecontext to EngineContext and axisservice into EngineConfiguration.
     *
     * @param axisService
     * @param serviceInputStream
     * @param classLoader
     * @return Returns AxisService.
     * @throws DeploymentException
     */
    public AxisService buildService(AxisService axisService, InputStream serviceInputStream,
                                    ClassLoader classLoader, AxisConfiguration axisConfig,
                                    boolean antiJARLocking)
            throws DeploymentException {
        try {
            ArchiveFileData currentArchiveFile = new ArchiveFileData(DeploymentConstants.TYPE_SERVICE, "", antiJARLocking);
            currentArchiveFile.setClassLoader(classLoader);

            ServiceBuilder builder = new ServiceBuilder(serviceInputStream, axisConfig,
                    axisService);

            builder.populateService(builder.buildOM());
        } catch (AxisFault axisFault) {
            throw new DeploymentException(axisFault);
        } catch (XMLStreamException e) {
            throw new DeploymentException(e);
        }

        return axisService;
    }
}