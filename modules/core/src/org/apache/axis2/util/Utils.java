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


package org.apache.axis2.util;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.AddressingConstants;
import org.apache.axis2.addressing.MessageInformationHeaders;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.context.ServiceGroupContext;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.description.Flow;
import org.apache.axis2.description.HandlerDescription;
import org.apache.axis2.description.InOutAxisOperation;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.ParameterImpl;
import org.apache.axis2.description.PhaseRule;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.engine.Handler;
import org.apache.axis2.engine.MessageReceiver;
import org.apache.axis2.receivers.AbstractMessageReceiver;
import org.apache.axis2.receivers.RawXMLINOutMessageReceiver;
import org.apache.wsdl.WSDLService;

import javax.xml.namespace.QName;
import java.io.File;

public class Utils {
    public static void addHandler(Flow flow, Handler handler, String phaseName) {
        HandlerDescription handlerDesc = new HandlerDescription();
        PhaseRule rule = new PhaseRule(phaseName);

        handlerDesc.setRules(rule);
        handler.init(handlerDesc);
        handlerDesc.setHandler(handler);
        flow.addHandler(handlerDesc);
    }

    public static MessageContext createOutMessageContext(MessageContext inMessageContext)
            throws AxisFault {
        MessageContext newmsgCtx = new MessageContext(inMessageContext.getConfigurationContext(),
                inMessageContext.getSessionContext(),
                inMessageContext.getTransportIn(),
                inMessageContext.getTransportOut());
        MessageInformationHeaders oldMessageInfoHeaders =
                inMessageContext.getMessageInformationHeaders();

        newmsgCtx.setMessageID(UUIDGenerator.getUUID());
        newmsgCtx.setTo(oldMessageInfoHeaders.getReplyTo());
        newmsgCtx.setFaultTo(oldMessageInfoHeaders.getFaultTo());
        newmsgCtx.setFrom(oldMessageInfoHeaders.getTo());
        newmsgCtx.setRelatesTo(
                new org.apache.axis2.addressing.RelatesTo(
                        oldMessageInfoHeaders.getMessageId(),
                        AddressingConstants.Submission.WSA_RELATES_TO_RELATIONSHIP_TYPE_DEFAULT_VALUE));
        newmsgCtx.setWSAAction(oldMessageInfoHeaders.getAction());
        newmsgCtx.setOperationContext(inMessageContext.getOperationContext());
        newmsgCtx.setServiceContext(inMessageContext.getServiceContext());
        newmsgCtx.setProperty(MessageContext.TRANSPORT_OUT,
                inMessageContext.getProperty(MessageContext.TRANSPORT_OUT));
        newmsgCtx.setProperty(Constants.OUT_TRANSPORT_INFO,
                inMessageContext.getProperty(Constants.OUT_TRANSPORT_INFO));

        // Setting the charater set encoding
        newmsgCtx.setProperty(MessageContext.CHARACTER_SET_ENCODING,
                inMessageContext.getProperty(MessageContext.CHARACTER_SET_ENCODING));
        newmsgCtx.setDoingREST(inMessageContext.isDoingREST());
        newmsgCtx.setDoingMTOM(inMessageContext.isDoingMTOM());
        newmsgCtx.setServerSide(inMessageContext.isServerSide());
        newmsgCtx.setServiceGroupContextId(inMessageContext.getServiceGroupContextId());

        return newmsgCtx;
    }

    public static AxisService createSimpleService(QName serviceName, String className, QName opName)
            throws AxisFault {
        return createSimpleService(serviceName, new RawXMLINOutMessageReceiver(), className,
                opName);
    }

    public static AxisService createSimpleService(QName serviceName,
                                                  MessageReceiver messageReceiver, String className, QName opName)
            throws AxisFault {
        AxisService service = new AxisService(serviceName.getLocalPart());

        service.setClassLoader(Thread.currentThread().getContextClassLoader());
        service.addParameter(new ParameterImpl(AbstractMessageReceiver.SERVICE_CLASS, className));

        // todo I assumed in-out mep , this has to be imroved : Deepal
        AxisOperation axisOp = new InOutAxisOperation(opName);

        axisOp.setMessageReceiver(messageReceiver);
        axisOp.setStyle(WSDLService.STYLE_RPC);
        service.addOperation(axisOp);

        return service;
    }

    public static void extractServiceGroupAndServiceInfo(String filePart,
                                                         MessageContext messageContext)
            throws AxisFault {
        String[] values = parseRequestURLForServiceAndOperation(filePart);
        String serviceNameAndGroup = values[0];

        if (serviceNameAndGroup != null) {
            String[]          serviceNameAndGroupStrings = serviceNameAndGroup.split(":");
            AxisConfiguration registry =
                    messageContext.getConfigurationContext().getAxisConfiguration();

            if (serviceNameAndGroupStrings[0] != null) {
                AxisServiceGroup axisServiceGroup =
                        registry.getServiceGroup(serviceNameAndGroupStrings[0]);
                String serviceNameStr = "";

                if (serviceNameAndGroupStrings.length == 1) {

                    // This means user has not given a service name.
                    // the notations is ...../axis2/services/<ServiceGroupName>
                    serviceNameStr = serviceNameAndGroupStrings[0];
                }

                AxisService axisService = registry.getService(serviceNameStr);

                if ((axisServiceGroup != null) && (axisService != null)) {
                    messageContext.setAxisServiceGroup(axisServiceGroup);
                    messageContext.setAxisService(axisService);
                }
            }
        }
    }

    public static ServiceContext fillContextInformation(AxisOperation axisOperation,
                                                        AxisService axisService, ConfigurationContext configurationContext)
            throws AxisFault {
        MessageContext msgContext;

        // 2. if null, create new opCtxt
        OperationContext operationContext = new OperationContext(axisOperation);

        // fill the service group context and service context info
        return fillServiceContextAndServiceGroupContext(axisService, configurationContext);
    }

    private static ServiceContext fillServiceContextAndServiceGroupContext(AxisService axisService,
                                                                           ConfigurationContext configurationContext)
            throws AxisFault {
        String serviceGroupContextId = UUIDGenerator.getUUID();
        ServiceGroupContext serviceGroupContext = new ServiceGroupContext(configurationContext,
                axisService.getParent());

        serviceGroupContext.setId(serviceGroupContextId);
        configurationContext.registerServiceGroupContext(serviceGroupContext);

        return new ServiceContext(axisService, serviceGroupContext);
    }

    /**
     * Break a full path into pieces
     *
     * @param path
     * @return an array where element [0] always contains the service, and element 1, if not null, contains
     *         the path after the first element. all ? parameters are discarded.
     */
    public static String[] parseRequestURLForServiceAndOperation(String path) {
        String[] values = new String[2];

        // TODO. This is kind of brittle. Any service with the name /services would cause fun.
        int index = path.lastIndexOf(Constants.REQUEST_URL_PREFIX);
        String service = null;

        if (-1 != index) {
            int serviceStart = index + Constants.REQUEST_URL_PREFIX.length();

            service = path.substring(serviceStart + 1);

            int queryIndex = service.indexOf('?');

            if (queryIndex > 0) {
                service = service.substring(0, queryIndex);
            }

            int operationIndex = service.indexOf('/');

            if (operationIndex > 0) {
                values[0] = service.substring(0, operationIndex);
                values[1] = service.substring(operationIndex + 1);
            } else {
                values[0] = service;
            }
        }

        return values;
    }

    public static ConfigurationContext getNewConfigurationContext(String repositry)
            throws Exception {
        ConfigurationContextFactory erfac = new ConfigurationContextFactory();
        File file = new File(repositry);

        if (!file.exists()) {
            throw new Exception("repository directory " + file.getAbsolutePath()
                    + " does not exists");
        }

        return erfac.buildConfigurationContext(file.getAbsolutePath());
    }

    public static String getParameterValue(Parameter param) {
        if (param == null) {
            return null;
        } else {
            return (String) param.getValue();
        }
    }
}
