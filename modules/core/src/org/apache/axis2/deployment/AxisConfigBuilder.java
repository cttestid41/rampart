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
*/


package org.apache.axis2.deployment;

import org.apache.axis2.AxisFault;
import org.apache.axis2.deployment.util.PhasesInfo;
import org.apache.axis2.deployment.util.Utils;
import org.apache.axis2.description.HandlerDescription;
import org.apache.axis2.description.ModuleConfiguration;
import org.apache.axis2.description.ParameterInclude;
import org.apache.axis2.description.TransportInDescription;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.engine.AxisObserver;
import org.apache.axis2.engine.MessageReceiver;
import org.apache.axis2.engine.Phase;
import org.apache.axis2.i18n.Messages;
import org.apache.axis2.om.OMAttribute;
import org.apache.axis2.om.OMElement;
import org.apache.axis2.phaseresolver.PhaseException;
import org.apache.axis2.transport.TransportListener;
import org.apache.axis2.transport.TransportSender;
import org.apache.axis2.util.HostConfiguration;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

public class AxisConfigBuilder extends DescriptionBuilder {
    private AxisConfiguration axisConfiguration;
    private DeploymentEngine engine;

    public AxisConfigBuilder(InputStream serviceInputSteram, DeploymentEngine engine,
                             AxisConfiguration axisConfiguration) {
        super(serviceInputSteram, axisConfiguration);
        this.axisConfiguration = axisConfiguration;
        this.engine = engine;
    }

    public void populateConfig() throws DeploymentException {
        try {
            OMElement config_element = buildOM();

            // processing Parameters
            // Processing service level parameters
            Iterator itr = config_element.getChildrenWithName(new QName(PARAMETER));

            processParameters(itr, axisConfiguration, axisConfiguration);

            // process MessageReciver
            Iterator msgRecives = config_element.getChildrenWithName(new QName(MESSAGERECEIVER));

            while (msgRecives.hasNext()) {
                OMElement msgRev = (OMElement) msgRecives.next();
                MessageReceiver msgrecivere =
                        loadMessageReceiver(Thread.currentThread().getContextClassLoader(), msgRev);
                OMAttribute mepAtt = msgRev.getAttribute(new QName(MEP));

                axisConfiguration.addMessageReceiver(mepAtt.getAttributeValue(), msgrecivere);
            }

            // Process Module refs
            Iterator moduleitr =
                    config_element.getChildrenWithName(new QName(DeploymentConstants.MODULEST));

            processModuleRefs(moduleitr);

            // Proccessing Transport Senders
            Iterator trs_senders = config_element.getChildrenWithName(new QName(TRANSPORTSENDER));

            processTransportSenders(trs_senders);

            // Proccessing Transport Receivers
            Iterator trs_Reivers = config_element.getChildrenWithName(new QName(TRANSPORTRECEIVER));

            processTransportReceivers(trs_Reivers);

            // Process Observers
            Iterator obs_ittr = config_element.getChildrenWithName(new QName(LISTENERST));

            processObservers(obs_ittr);

            // processing Phase orders
            Iterator phaserders = config_element.getChildrenWithName(new QName(PHASE_ORDER));

            processPhaseOrders(phaserders);

            Iterator moduleConfigs = config_element.getChildrenWithName(new QName(MODULECONFIG));

            processModuleConfig(moduleConfigs, axisConfiguration, axisConfiguration);

            // setting host configuration
            OMElement hostElement = config_element.getFirstChildWithName(new QName(HOST_CONFIG));

            if (hostElement != null) {
                processHostCongiguration(hostElement, axisConfiguration);
            }
        } catch (XMLStreamException e) {
            throw new DeploymentException(e);
        }
    }

    private void processHostCongiguration(OMElement element, AxisConfiguration config) {
        OMElement ipele = element.getFirstChildWithName(new QName("ip"));
        String ip = null;
        int port = -1;

        if (ipele != null) {
            ip = ipele.getText().trim();
        }

        OMElement portele = element.getFirstChildWithName(new QName("port"));

        if (portele != null) {
            port = Integer.parseInt(portele.getText().trim());
        }

        HostConfiguration hostconfig = new HostConfiguration(ip, port);

        config.setHostConfiguration(hostconfig);
    }

    protected void processModuleConfig(Iterator moduleConfigs, ParameterInclude parent,
                                       AxisConfiguration config)
            throws DeploymentException {
        while (moduleConfigs.hasNext()) {
            OMElement moduleConfig = (OMElement) moduleConfigs.next();
            OMAttribute moduleName_att = moduleConfig.getAttribute(new QName(ATTNAME));

            if (moduleName_att == null) {
                throw new DeploymentException(
                        Messages.getMessage(DeploymentErrorMsgs.INVALID_MODULE_CONFIG));
            } else {
                String module = moduleName_att.getAttributeValue();
                ModuleConfiguration moduleConfiguration =
                        new ModuleConfiguration(new QName(module), parent);
                Iterator parameters = moduleConfig.getChildrenWithName(new QName(PARAMETER));

                processParameters(parameters, moduleConfiguration, parent);
                config.addModuleConfig(moduleConfiguration);
            }
        }
    }

    /**
     * To get the list og modules that is requird to be engage globally
     *
     * @param moduleRefs <code>java.util.Iterator</code>
     */
    protected void processModuleRefs(Iterator moduleRefs) {
        while (moduleRefs.hasNext()) {
            OMElement moduleref = (OMElement) moduleRefs.next();
            OMAttribute moduleRefAttribute = moduleref.getAttribute(new QName(REF));
            String refName = moduleRefAttribute.getAttributeValue();

            engine.addModule(new QName(refName));
        }
    }

    /**
     * To process AxisObservers
     *
     * @param oservers
     */
    private void processObservers(Iterator oservers) throws DeploymentException {
        while (oservers.hasNext()) {
            OMElement observerelement = (OMElement) oservers.next();
            AxisObserver observer;
            OMAttribute trsClas = observerelement.getAttribute(new QName(CLASSNAME));
            String clasName;

            if (trsClas != null) {
                clasName = trsClas.getAttributeValue();
            } else {
                throw new DeploymentException(
                        Messages.getMessage(DeploymentErrorMsgs.OBSERVER_ERROR));
            }

            try {
                Class observerclass = Class.forName(clasName, true,
                        Thread.currentThread().getContextClassLoader());

                observer = (AxisObserver) observerclass.newInstance();

                // processing Parameters
                // Processing service level parameters
                Iterator itr = observerelement.getChildrenWithName(new QName(PARAMETER));

                processParameters(itr, observer, axisConfiguration);

                // initialization
                observer.init();
                axisConfiguration.addObservers(observer);
            } catch (ClassNotFoundException e) {
                throw new DeploymentException(e);
            } catch (IllegalAccessException e) {
                throw new DeploymentException(e);
            } catch (InstantiationException e) {
                throw new DeploymentException(e);
            }
        }
    }

    private ArrayList processPhaseList(OMElement phaseOrders) throws DeploymentException {
        ArrayList phaselist = new ArrayList();
        Iterator phases = phaseOrders.getChildrenWithName(new QName(PHASE));

        while (phases.hasNext()) {
            OMElement phaseelement = (OMElement) phases.next();
            String phaseName =
                    phaseelement.getAttribute(new QName(ATTNAME)).getAttributeValue();
            String phaseClass = phaseelement.getAttributeValue(new QName(CLASSNAME));
            Phase phase;

            try {
                phase = getPhase(phaseClass);
            } catch (Exception e) {
                throw new DeploymentException("Couldn't find phase class : " + phaseClass, e);
            }

            phase.setName(phaseName);

            Iterator handlers = phaseelement.getChildrenWithName(new QName(HANDERST));

            while (handlers.hasNext()) {
                OMElement omElement = (OMElement) handlers.next();
                HandlerDescription handler = processHandler(omElement, axisConfiguration);

                handler.getRules().setPhaseName(phaseName);
                Utils.loadHandler(axisConfiguration.getSystemClassLoader(), handler);

                try {
                    phase.addHandler(handler);
                } catch (PhaseException e) {
                    throw new DeploymentException(e);
                }
            }

            phaselist.add(phase);
        }

        return phaselist;
    }

    /**
     * To process all the phase orders which are defined in axis2.xml
     *
     * @param phaserders
     */
    private void processPhaseOrders(Iterator phaserders) throws DeploymentException {
        PhasesInfo info = engine.getPhasesinfo();

        while (phaserders.hasNext()) {
            OMElement phaseOrders = (OMElement) phaserders.next();
            String flowType = phaseOrders.getAttribute(new QName(TYPE)).getAttributeValue();

            if (INFLOWST.equals(flowType)) {
                info.setINPhases(processPhaseList(phaseOrders));
            } else if (IN_FAILTFLOW.equals(flowType)) {
                info.setIN_FaultPhases(processPhaseList(phaseOrders));
            } else if (OUTFLOWST.equals(flowType)) {
                info.setOUTPhases(processPhaseList(phaseOrders));
            } else if (OUT_FAILTFLOW.equals(flowType)) {
                info.setOUT_FaultPhases(processPhaseList(phaseOrders));
            }
        }
    }

    private void processTransportReceivers(Iterator trs_senders) throws DeploymentException {
        while (trs_senders.hasNext()) {
            TransportInDescription transportIN;
            OMElement transport = (OMElement) trs_senders.next();

            // getting transport Name
            OMAttribute trsName = transport.getAttribute(new QName(ATTNAME));

            if (trsName != null) {
                String name = trsName.getAttributeValue();

                transportIN = new TransportInDescription(new QName(name));

                // transport impl class
                OMAttribute trsClas = transport.getAttribute(new QName(CLASSNAME));

                if (trsClas != null) {
                    try {
                        String clasName = trsClas.getAttributeValue();
                        Class receiverClass = Class.forName(clasName, true,
                                Thread.currentThread().getContextClassLoader());
                        TransportListener receiver =
                                (TransportListener) receiverClass.newInstance();

                        transportIN.setReceiver(receiver);
                    } catch (NoClassDefFoundError e) {
                        log.info(Messages.getMessage("classnotfound", trsClas.getAttributeValue()));
                    } catch (ClassNotFoundException e) {
                        throw new DeploymentException(e);
                    } catch (IllegalAccessException e) {
                        throw new DeploymentException(e);
                    } catch (InstantiationException e) {
                        throw new DeploymentException(e);
                    }
                }

                try {

                    // process Parameters
                    // processing Parameters
                    // Processing service level parameters
                    Iterator itr = transport.getChildrenWithName(new QName(PARAMETER));

                    processParameters(itr, transportIN, axisConfiguration);

                    // process INFLOW
                    OMElement inFlow = transport.getFirstChildWithName(new QName(INFLOWST));

                    if (inFlow != null) {
                        throw new DeploymentException(
                                Messages.getMessage(
                                        DeploymentErrorMsgs.INFLOW_NOT_ALLOWED_IN_TRS_OUT, name));
                    }

                    OMElement outFlow = transport.getFirstChildWithName(new QName(OUTFLOWST));

                    if (outFlow != null) {
                        transportIN.setInFlow(processFlow(outFlow, axisConfiguration));
                    }

                    OMElement inFaultFlow =
                            transport.getFirstChildWithName(new QName(IN_FAILTFLOW));

                    if (inFaultFlow != null) {
                        transportIN.setFaultFlow(processFlow(inFaultFlow, axisConfiguration));
                    }

                    OMElement outFaultFlow =
                            transport.getFirstChildWithName(new QName(OUT_FAILTFLOW));

                    if (outFaultFlow != null) {
                        throw new DeploymentException(
                                Messages.getMessage(
                                        DeploymentErrorMsgs.OUTFLOW_NOT_ALLOWED_IN_TRS_IN, name));
                    }

                    // adding to axis config
                    axisConfiguration.addTransportIn(transportIN);
                } catch (AxisFault axisFault) {
                    throw new DeploymentException(axisFault);
                }
            }
        }
    }

    private void processTransportSenders(Iterator trs_senders) throws DeploymentException {
        while (trs_senders.hasNext()) {
            TransportOutDescription transportout;
            OMElement transport = (OMElement) trs_senders.next();

            // getting transport Name
            OMAttribute trsName = transport.getAttribute(new QName(ATTNAME));

            if (trsName != null) {
                String name = trsName.getAttributeValue();

                transportout = new TransportOutDescription(new QName(name));

                // transport impl class
                OMAttribute trsClas = transport.getAttribute(new QName(CLASSNAME));

                if (trsClas == null) {
                    throw new DeploymentException(
                            Messages.getMessage(DeploymentErrorMsgs.TRANSPORT_SENDER_ERROR, name));
                }

                String clasName = trsClas.getAttributeValue();
                Class sender;

                try {
                    sender = Class.forName(clasName, true,
                            Thread.currentThread().getContextClassLoader());

                    TransportSender transportSender = (TransportSender) sender.newInstance();

                    transportout.setSender(transportSender);

                    // process Parameters
                    // processing Parameters
                    // Processing service level parameters
                    Iterator itr = transport.getChildrenWithName(new QName(PARAMETER));

                    processParameters(itr, transportout, axisConfiguration);

                    // process INFLOW
                    OMElement inFlow = transport.getFirstChildWithName(new QName(INFLOWST));

                    if (inFlow != null) {
                        throw new DeploymentException(
                                Messages.getMessage(
                                        DeploymentErrorMsgs.INFLOW_NOT_ALLOWED_IN_TRS_OUT, name));
                    }

                    OMElement outFlow = transport.getFirstChildWithName(new QName(OUTFLOWST));

                    if (outFlow != null) {
                        transportout.setOutFlow(processFlow(outFlow, axisConfiguration));
                    }

                    OMElement inFaultFlow =
                            transport.getFirstChildWithName(new QName(IN_FAILTFLOW));

                    if (inFaultFlow != null) {
                        throw new DeploymentException(
                                Messages.getMessage(
                                        DeploymentErrorMsgs.INFLOW_NOT_ALLOWED_IN_TRS_OUT, name));
                    }

                    OMElement outFaultFlow =
                            transport.getFirstChildWithName(new QName(OUT_FAILTFLOW));

                    if (outFaultFlow != null) {
                        transportout.setFaultFlow(processFlow(outFaultFlow, axisConfiguration));
                    }

                    // adding to axis config
                    axisConfiguration.addTransportOut(transportout);
                } catch (ClassNotFoundException e) {
                    throw new DeploymentException(e);
                } catch (IllegalAccessException e) {
                    throw new DeploymentException(e);
                } catch (InstantiationException e) {
                    throw new DeploymentException(e);
                } catch (AxisFault axisFault) {
                    throw new DeploymentException(axisFault);
                }
            }
        }
    }

    private Phase getPhase(String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (className == null) {
            return new Phase();
        }

        Class phaseClass = axisConfiguration.getSystemClassLoader().loadClass(className);

        return (Phase) phaseClass.newInstance();
    }
}
