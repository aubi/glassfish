/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.enterprise.security.jmac.provider;

import com.sun.enterprise.deployment.MethodDescriptor;
import com.sun.enterprise.deployment.runtime.common.MessageDescriptor;
import com.sun.enterprise.deployment.runtime.common.MessageSecurityDescriptor;
import com.sun.enterprise.deployment.runtime.common.ProtectionDescriptor;
import com.sun.enterprise.security.jauth.AuthContext;
import com.sun.enterprise.security.jauth.AuthException;
import com.sun.enterprise.security.jauth.AuthPolicy;
import com.sun.enterprise.security.webservices.LogUtils;

import jakarta.xml.soap.MimeHeaders;
import jakarta.xml.soap.Name;
import jakarta.xml.soap.Node;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.soap.SOAPPart;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.xml.namespace.QName;

/**
 * This class is the container's base interface to the AuthConfig subsystem
 * to get AuthContext objects on which to invoke message layer authentication
 * providers. It is not intended to be layer or web services specific (see
 * getMechanisms method at end).
 * The ServerAuthConfig and ClientAuthConfig classes extend this class.
 */
public class BaseAuthConfig {

    // WSS security header QName
    private static QName mechanisms[] = new QName[] {
        new QName( "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd", "Security", "wsse") };

    private static final Logger LOG = LogUtils.getLogger();

    private AuthContext defaultContext;

    // holds protected msd that applies to all methods (if there is one)
    private MessageSecurityDescriptor superMSD;
    private int superIndex;

    private final List<? extends AuthContext> contexts;

    private final List<MessageSecurityDescriptor> messageSecurityDescriptors;

    private HashMap<String, Integer> contextsForOpNames;

    private boolean onePolicy;

    private final Object contextLock = new Object();

    private final ExplicitNull explicitNull = new ExplicitNull();

    protected BaseAuthConfig(AuthContext context) {

        defaultContext = context;
        superMSD = null;
        superIndex = -1;

        messageSecurityDescriptors = null;
        contexts = null;
        contextsForOpNames = null;

        onePolicy = true;

        LOG.log(Level.FINE, "WSS: New BAC defaultContext: {0}", defaultContext);
    }


    protected BaseAuthConfig(List<MessageSecurityDescriptor> descriptors, List<? extends AuthContext> authContexts) {
        defaultContext = null;
        superMSD = null;
        superIndex = -1;

        messageSecurityDescriptors = descriptors;
        contexts = authContexts;
        contextsForOpNames = null;

        onePolicy = true;

        for (int i = 0; i < descriptors.size(); i++) {

            MessageSecurityDescriptor msd = descriptors.get(i);

            // determine if all the different messageSecurityDesriptors have the
            // same policy which will help us interpret the effective policy if
            // we cannot determine the opcode of a request at runtime.

            for (int j = 0; j < descriptors.size(); j++) {
                if (j != i && !policiesAreEqual(msd, (descriptors.get(j)))) {
                    onePolicy = false;
                }
            }
        }

        for (int i = 0; defaultContext == null && i < descriptors.size(); i++) {

            MessageSecurityDescriptor msd = descriptors.get(i);

            AuthPolicy requestPolicy = getAuthPolicy(msd.getRequestProtectionDescriptor());
            AuthPolicy responsePolicy = getAuthPolicy(msd.getResponseProtectionDescriptor());

            boolean noProtection = !requestPolicy.authRequired() && !responsePolicy.authRequired();

            // if there is one policy, and it is null set the associated context as the
            // defaultContext used for all messages.
            if (i == 0 && onePolicy && noProtection) {
                defaultContext = explicitNull;
                break;
            }

            List<MessageDescriptor> mDs = msd.getMessageDescriptors();

            for (int j = 0; mDs != null && j < mDs.size(); j++) {

                MessageDescriptor mD = mDs.get(j);
                MethodDescriptor methD = mD.getMethodDescriptor();

                // if any msd covers all methods and operations.
                if ((mD.getOperationName() == null && methD == null) || (methD != null && methD.getStyle() == 1)) {

                    if (onePolicy) {
                        // if there is only one policy make it the default.
                        defaultContext = contexts.get(i);
                        if (defaultContext == null) {
                            defaultContext = explicitNull;
                        }
                        break;
                    } else if (superIndex == -1) {
                        // if it has a noProtection policy make it the default.
                        if (noProtection) {
                            defaultContext = explicitNull;
                        } else {
                            superIndex = i;
                        }
                    } else if (!policiesAreEqual(msd, (descriptors.get(superIndex)))) {
                        // if there are conflicting policies that cover all methods
                        // set the default policy to noProtection
                        defaultContext = explicitNull;
                        superIndex = -1;
                        break;
                    }
                }
            }
        }
        // if there is protected policy that applies to all methods remember the descriptor.
        // Note that the corresponding policy is not null, and thus it is not the default.
        // wherever there is a conflicting policy the effective policy will be noProtection.
        if (superIndex >= 0) {
            superMSD = descriptors.get(superIndex);
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "WSS: new BAC defaultContext: {0} superMSD index: {1} onePolicy_: {2}",
                new Object[] {defaultContext, superIndex, onePolicy});
        }
    }


    protected static AuthPolicy getAuthPolicy(ProtectionDescriptor pd) {
        int sourceAuthType = AuthPolicy.SOURCE_AUTH_NONE;
        boolean recipientAuth = false;
        boolean beforeContent = false;
        if (pd != null) {
            String source = pd.getAttributeValue(ProtectionDescriptor.AUTH_SOURCE);
            if (source != null) {
                if (source.equals(AuthPolicy.SENDER)) {
                    sourceAuthType = AuthPolicy.SOURCE_AUTH_SENDER;
                } else if (source.equals(AuthPolicy.CONTENT)) {
                    sourceAuthType = AuthPolicy.SOURCE_AUTH_CONTENT;
                }
            }
            String recipient = pd.getAttributeValue(ProtectionDescriptor.AUTH_RECIPIENT);
            if (recipient != null) {
                recipientAuth = true;
                if (recipient.equals(AuthPolicy.BEFORE_CONTENT)) {
                    beforeContent = true;
                } else if (recipient.equals(AuthPolicy.AFTER_CONTENT)) {
                    beforeContent = false;
                }
            }
        }
        return new AuthPolicy(sourceAuthType, recipientAuth, beforeContent);
    }


    private static boolean isMatchingMSD(MethodDescriptor targetMD, MessageSecurityDescriptor mSD) {
        List<MessageDescriptor> messageDescriptors = mSD.getMessageDescriptors();
        if (messageDescriptors.isEmpty()) {
            // If this happens the dd is invalid.
            // Unfortunately the deployment system does not catch such problems.
            // This case will be treated the same as if there was an empty message
            // element, and the deployment will be allowed to succeed.
            return true;
        }

        for (MessageDescriptor nextMD : messageDescriptors) {
            MethodDescriptor mD = nextMD.getMethodDescriptor();
            String opName = nextMD.getOperationName();

            if (opName == null && (mD == null || mD.implies(targetMD))) {
                return true;
            }
        }

        return false;
    }


    private static boolean policiesAreEqual(MessageSecurityDescriptor reference, MessageSecurityDescriptor other) {
        if (!getAuthPolicy(reference.getRequestProtectionDescriptor())
            .equals(getAuthPolicy(other.getRequestProtectionDescriptor()))
            || !getAuthPolicy(reference.getResponseProtectionDescriptor())
                .equals(getAuthPolicy(other.getResponseProtectionDescriptor()))) {

            return false;
        }
        return true;
    }


    /*
     * When method argument is null, returns the default AC
     * if there is one, or the onePolicy shared by all methods
     * if there is one, or throws an error.
     * method is called with null argument when the method
     * cannot be determined (e.g. when the message is encrypted)
     */
    private Object getContextForMethod(Method m) {
        Object rvalue = null;
        synchronized (contextLock) {
            if (defaultContext != null) {
                rvalue = defaultContext;
                LOG.log(Level.FINE, "WSS: ForMethod returning default_context: {0}", rvalue);
                return rvalue;
            }
        }
        if (m != null) {
            int match = -1;
            MethodDescriptor targetMD = new MethodDescriptor(m);
            for (int i = 0; i < messageSecurityDescriptors.size(); i++) {
                if (isMatchingMSD(targetMD, messageSecurityDescriptors.get(i))) {
                    if (match < 0) {
                        match = i;
                    } else if (!policiesAreEqual(messageSecurityDescriptors.get(match),
                        messageSecurityDescriptors.get(i))) {

                        // set to unprotected because of conflicting policies

                        rvalue = explicitNull;
                        match = -1;
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.log(Level.FINE, "WSS: ForMethod detected conflicting policies: {0}.{1}",
                                new Object[] {match, i});
                        }
                        break;
                    }
                }
            }
            if (match >= 0) {
                rvalue = contexts.get(match);
                if (rvalue == null) {
                    rvalue = explicitNull;
                }
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "WSS: ForMethod returning matched context: {0}", rvalue);
                }
            }
        } else if (onePolicy && !contexts.isEmpty()) {
            // ISSUE: since the method is undefined we will not be
            // able to tell if the defined policy covers this method.
            // We will be optimistic and try the policy, because
            // the server will reject the call if the method is not
            // covered by the policy.
            // If the policy is not null, there remains a problem at the
            // client on the response side, as it is possible that the
            // client will enforce the wrong policy on the response.
            // For this reason, messages in sun-application-client.xml
            // should be keyed by operation-name.

            rvalue = contexts.get(0);
            LOG.log(Level.FINE, "WSS: ForMethod resorting to first context: {0}", rvalue);

        } else {
            throw new RuntimeException("Unable to select policy for Message");
        }
        return rvalue;
    }


    private static String getOpName(SOAPMessage message) {
        String rvalue = null;

        // first look for a SOAPAction header.
        // this is what .net uses to identify the operation

        MimeHeaders headers = message.getMimeHeaders();
        if (headers != null) {
            String[] actions = headers.getHeader("SOAPAction");
            if (actions != null && actions.length > 0) {
                rvalue = actions[0];
                if (rvalue != null && rvalue.equals("\"\"")) {
                    rvalue = null;
                }
            }
        }

        // if that doesn't work then we default to trying the name
        // of the first child element of the SOAP envelope.

        if (rvalue == null) {
            Name name = getName(message);
            if (name != null) {
                rvalue = name.getLocalName();
            }
        }

        return rvalue;
    }

    private static String getOpName(SOAPMessageContext soapMC) {

        String rvalue;

        // first look for a the property value in the context
        QName qName = (QName) soapMC.get(MessageContext.WSDL_OPERATION);
        if (qName != null) {
            rvalue = qName.getLocalPart();
        } else {
            rvalue = getOpName(soapMC.getMessage());
        }

        return rvalue;
    }

    private Object getContextForOpName(String operation) {
        synchronized (contextLock) {
            if (contextsForOpNames == null) {

                // one time initialization of the opName to authContext array.

                contextsForOpNames = new HashMap<>();
                for (int i = 0; messageSecurityDescriptors != null && i < messageSecurityDescriptors.size(); i++) {

                    MessageSecurityDescriptor mSD = messageSecurityDescriptors.get(i);

                    List<MessageDescriptor> mDs = mSD.getMessageDescriptors();

                    for (int j = 0; mDs != null && j < mDs.size(); j++) {

                        MessageDescriptor mD = mDs.get(j);
                        String opName = mD.getOperationName();

                        if (opName != null) {

                            if (contextsForOpNames.containsKey(opName)) {

                                Integer k = contextsForOpNames.get(opName);
                                if (k != null) {

                                    MessageSecurityDescriptor other = messageSecurityDescriptors.get(k.intValue());

                                    // set to null if different policies on operation

                                    if (!policiesAreEqual(mSD, other)) {
                                        contextsForOpNames.put(opName, null);
                                    }
                                }
                            } else if (superMSD != null && !policiesAreEqual(mSD, superMSD)) {
                                // set to null if operation policy differs from superPolicy
                                contextsForOpNames.put(opName, null);
                            } else {
                                contextsForOpNames.put(opName, Integer.valueOf(i));
                            }
                        }
                    }
                }
            }
        }

        Object rvalue = null;
        if (operation != null) {
            if (contextsForOpNames.containsKey(operation)) {
                Integer k = contextsForOpNames.get(operation);
                if (k != null) {
                    rvalue = contexts.get(k.intValue());
                }
            } else if (superIndex >= 0) {
                // if there is a msb that matches all methods, use the
                // associatedContext
                rvalue = contexts.get(superIndex);
            }

            if (rvalue == null) {
                // else return explicitNull under the assumption
                // that methodName was known, and no match was found
                rvalue = explicitNull;
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "WSS: ForOpName={0} context: {1}", new Object[]{operation, rvalue});
            }
        }
        return rvalue;
    }

    // DO NOT CALL THIS ON THE SERVER SIDE, as it will return a null
    // context if there is no default context and there isn't a message
    // element defined with the corresponding operation name (even though the
    // corresponding method may be protected).
    //
    // This method is intended to be used by clients where it serves as a
    // work-around for not being able to map the message to the method (due
    // to lack of access to a streaming handler equivalent).
    //
    // This method will not be called when the handler argument passed in
    // a call to getContext or getContextForOpCode is not null.
    // Thus, server-side calls to these methods must pass a non-null
    // handler argument.

    private Object getContextForMessage(SOAPMessage message) {

        String opName = getOpName(message);

        Object rvalue = getContextForOpName(opName);
        if (rvalue == null) {

            // opName is not mapped or msg body is encrypted, and the best
            // we can do is try to return a policy that applies to all
            // operations, if there is one.

            rvalue = getContextForMethod(null);

        }
        return rvalue;
    }


    // used by jaxws system handler delegates and handlers
    protected Object getContext(SOAPMessageContext soapMC) {

        Object rvalue = null;

        synchronized(contextLock) {
            if (defaultContext != null) {
                rvalue = defaultContext;
            }
        }

        if (rvalue == null) {

            Method m = getMethod(soapMC);
            String opName = null;

            if (m != null) {
                rvalue = getContextForMethod(m);
            }

            if (rvalue == null) {
                opName = getOpName(soapMC);
                if (opName != null) {
                    rvalue = getContextForOpName(opName);
                }
            }

            if (rvalue == null && (m == null || opName == null)) {

                //we were unable to determine either method or
                // opName, so lets see if one policy applies to all

                rvalue = getContextForMethod(null);
            }
        }

        if (rvalue != null && rvalue instanceof ExplicitNull) {
            rvalue = null;
        }

        return rvalue;
    }

    private static Name getName(SOAPMessage message) {
        Name rvalue = null;
        SOAPPart soap = message.getSOAPPart();
        if (soap != null) {
            try {
                SOAPEnvelope envelope = soap.getEnvelope();
                if (envelope != null) {
                    SOAPBody body = envelope.getBody();
                    if (body != null) {
                        Iterator<Node> it = body.getChildElements();
                        while (it.hasNext()) {
                            Node o = it.next();
                            if (o instanceof SOAPElement) {
                                rvalue = ((SOAPElement) o).getElementName();
                                break;
                            }
                        }
                    }
                }
            } catch (SOAPException se) {
                if(LOG.isLoggable(Level.FINE)){
                    LOG.log(Level.FINE,"WSS: Unable to get SOAP envelope",
                        se);
                }
            }
        }

        return rvalue;
    }

    public static Method getMethod(SOAPMessageContext soapMC) {

        // It should never come here
        return null;
    }

    // each instance of AuthConfig maps to one provider
    // configuration, either via a message-security-binding, or a default
    // provider-config.

    // mechanisms are temporarily encapsulated here, until a method that
    // returns the list of supported mechanisms is added to
    // jauth.ServerAuthContext and jauth.ClientAuthContext.
    public QName[] getMechanisms() {
        return mechanisms;
    }

    /**
     * Internal class used to differentiate not protected from policy undefined or not determined.
     */
    private static final class ExplicitNull implements AuthContext {

        @Override
        public boolean equals(Object other) {
            return (other != null && other instanceof ExplicitNull ? true : false);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public String toString() {
            return "ExplicitNull";
        }

        @Override
        public void disposeSubject(Subject subject, Map sharedState) throws AuthException {
            // doesn't do anything
        }
    }
}
