/**
 * Licensed to the Austrian Association for Software Tool Integration (AASTI)
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. The AASTI licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openengsb.core.services.internal;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openengsb.core.api.Connector;
import org.openengsb.core.api.ConnectorInstanceFactory;
import org.openengsb.core.api.ConnectorValidationFailedException;
import org.openengsb.core.api.Constants;
import org.openengsb.core.api.Domain;
import org.openengsb.core.api.DomainProvider;
import org.openengsb.core.api.LinkingSupport;
import org.openengsb.core.api.OsgiServiceNotAvailableException;
import org.openengsb.core.api.model.ConnectorDescription;
import org.openengsb.core.api.model.ModelDescription;
import org.openengsb.core.api.persistence.ConfigPersistenceService;
import org.openengsb.core.api.xlink.model.ModelViewMapping;
import org.openengsb.core.api.xlink.model.XLinkConnectorRegistration;
import org.openengsb.core.api.xlink.model.XLinkConnectorView;
import org.openengsb.core.api.xlink.service.XLinkConnectorManager;
import org.openengsb.core.common.SecurityAttributeProviderImpl;
import org.openengsb.core.ekb.api.TransformationEngine;
import org.openengsb.core.persistence.internal.DefaultConfigPersistenceService;
import org.openengsb.core.services.internal.model.NullConnector;
import org.openengsb.core.services.internal.model.NullModel;
import org.openengsb.core.services.internal.xlink.ExampleObjectOrientedModel;
import org.openengsb.core.test.AbstractOsgiMockServiceTest;
import org.openengsb.core.test.DummyConfigPersistenceService;
import org.openengsb.core.test.DummyModel;
import org.openengsb.core.test.NullDomain;
import org.openengsb.core.test.NullDomainImpl;
import org.openengsb.core.util.DefaultOsgiUtilsService;

public class ConnectorManagerTest extends AbstractOsgiMockServiceTest {

    private DefaultOsgiUtilsService serviceUtils;
    private DefaultOsgiUtilsService mockedServiceUtils;
    private XLinkConnectorManager connectorManager;
    private ConnectorRegistrationManager serviceRegistrationManagerImpl;
    private ConnectorInstanceFactory factory;
    private DefaultConfigPersistenceService configPersistence;

    private LinkingSupport testConnector;

    private TransformationEngine transformationEngine;

    @Before
    public void setUp() throws Exception {
        registerMockedDomainProvider();
        registerMockedFactory();
        registerConfigPersistence();
        transformationEngine = mock(TransformationEngine.class);
        when(transformationEngine
            .performTransformation(any(ModelDescription.class), any(ModelDescription.class), any()))
            .thenAnswer(new Answer<Object>() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    return invocation.getArguments()[2];
                }
            });
        MethodInterceptor securityInterceptor = new ForwardMethodInterceptor();
        serviceRegistrationManagerImpl = new ConnectorRegistrationManager(bundleContext, transformationEngine,
            securityInterceptor, new SecurityAttributeProviderImpl());
        serviceUtils = new DefaultOsgiUtilsService(bundleContext);
        mockedServiceUtils = mock(DefaultOsgiUtilsService.class);
        mockedServiceUtils = mock(DefaultOsgiUtilsService.class);
        testConnector = mock(LinkingSupport.class);
        LinkingSupport testConnector2 = mock(LinkingSupport.class);
        Domain testConnector3 = mock(Domain.class);
        when(mockedServiceUtils.getService("(service.pid=test+test+test)", 100L)).thenReturn(testConnector);
        when(mockedServiceUtils.getService("(service.pid=test2+test2+test2)", 100L)).thenReturn(testConnector2);
        when(mockedServiceUtils.getService("(service.pid=test3+test3+test3)", 100L)).thenReturn(null);
        when(mockedServiceUtils.getService("(service.pid=test4+test4+test4)", 100L)).thenReturn(testConnector3);
        createServiceManager();
    }

    private void registerConfigPersistence() {
        DummyConfigPersistenceService<String> backend = new DummyConfigPersistenceService<String>();
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(Constants.CONFIGURATION_ID, Constants.CONFIG_CONNECTOR);
        props.put(Constants.BACKEND_ID, "dummy");
        configPersistence = new DefaultConfigPersistenceService(backend);
        registerService(configPersistence, props, ConfigPersistenceService.class);
    }

    private void createServiceManager() {
        XLinkConnectorManagerImpl serviceManagerImpl = new XLinkConnectorManagerImpl();
        serviceManagerImpl.setRegistrationManager(serviceRegistrationManagerImpl);
        serviceManagerImpl.setConfigPersistence(configPersistence);
        serviceManagerImpl.setxLinkBaseUrl("http://localhost/openXLink");
        serviceManagerImpl.setxLinkExpiresIn(3);
        serviceManagerImpl.setUtilsService(mockedServiceUtils);
        connectorManager = serviceManagerImpl;
    }

    @Test
    public void testCreateService_shouldCreateInstanceWithFactory() throws Exception {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("answer", "42");
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        connectorManager.create(connectorDescription);

        serviceUtils.getService("(foo=bar)", 100L);
    }

    @Test
    public void testCreateService_shouldExist() throws Exception {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("answer", "42");
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        String id = connectorManager.create(connectorDescription);

        boolean exists = connectorManager.connectorExists(id);
        assertTrue("Service doesn't exist after creation", exists);
    }

    @Test
    public void testUpdateService_shouldUpdateInstance() throws Exception {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("answer", "42");
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        String uuid = connectorManager.create(connectorDescription);

        connectorDescription.getProperties().put("foo", "42");
        connectorDescription.getAttributes().put("answer", "43");
        connectorManager.update(uuid, connectorDescription);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreateServiceWithInvalidAttributes_shouldFail() throws Exception {
        Map<String, String> errorMessages = new HashMap<String, String>();
        errorMessages.put("all", "because I don't like you");
        when(factory.getValidationErrors(anyMap())).thenReturn(errorMessages);
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("answer", "42");
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        try {
            connectorManager.create(connectorDescription);
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertThat(((ConnectorValidationFailedException) e.getCause()).getErrorMessages(), is(errorMessages));
        }

        try {
            serviceUtils.getService(NullDomain.class, 100L);
            fail("service is available, but shouldn't be");
        } catch (OsgiServiceNotAvailableException e) {
            // expected. No service should be available because the attributes were invalid
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testForceCreateServiceWithInvalidAttributes_shouldCreateConnector() throws Exception {
        Map<String, String> errorMessages = new HashMap<String, String>();
        errorMessages.put("all", "because I don't like you");
        when(factory.getValidationErrors(anyMap())).thenReturn(errorMessages);
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("answer", "42");
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        connectorManager.forceCreate(connectorDescription);

        try {
            serviceUtils.getService("(foo=bar)", 100L);
        } catch (OsgiServiceNotAvailableException e) {
            fail("service should be available because validation should have been skipped");
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdateServiceWithInvalidAttributes_shouldLeaveServiceUnchanged() throws Exception {
        Map<String, String> errorMessages = new HashMap<String, String>();
        errorMessages.put("all", "because I don't like you");
        when(factory.getValidationErrors(any(Connector.class), anyMap())).thenReturn(errorMessages);

        Map<String, String> attributes = new HashMap<String, String>();
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        String connectorId = connectorManager.create(connectorDescription);
        serviceUtils.getService("(foo=bar)", 1L);

        connectorDescription.getProperties().put("foo", "42");
        try {
            connectorManager.update(connectorId, connectorDescription);
            fail("Exception expected");
        } catch (RuntimeException e) {
            assertThat(((ConnectorValidationFailedException) e.getCause()).getErrorMessages(), is(errorMessages));
        }

        try {
            serviceUtils.getService("(foo=bar)", 1L);
        } catch (OsgiServiceNotAvailableException e) {
            fail("Service is not available with the old attributes");
        }

        try {
            serviceUtils.getService("(foo=42)", 1L);
            fail("Service should not be available with the new properties, but it is");
        } catch (OsgiServiceNotAvailableException e) {
            // expected. The properties should not have been updated, so no service is available.
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testForceUpdateServiceWithInvalidAttributes_shouldUpdateService() throws Exception {
        Map<String, String> errorMessages = new HashMap<String, String>();
        errorMessages.put("all", "because I don't like you");
        when(factory.getValidationErrors(any(Connector.class), anyMap())).thenReturn(errorMessages);

        Map<String, String> attributes = new HashMap<String, String>();
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        String connectorId = connectorManager.create(connectorDescription);
        serviceUtils.getService("(foo=bar)", 1L);

        connectorDescription.getProperties().put("foo", "42");
        connectorManager.forceUpdate(connectorId, connectorDescription);

        try {
            serviceUtils.getService("(foo=bar)", 1L);
            fail("Service is only available with the old attributes");
        } catch (OsgiServiceNotAvailableException e) {
            // expected. The attributes have been overwritten
        }

        try {
            serviceUtils.getService("(foo=42)", 1L);
        } catch (OsgiServiceNotAvailableException e) {
            fail("Service should be available with the new properties, since validation should have been skipped");
        }
    }

    @Test
    public void testDeleteService_shouldNotBeAvailableAnymore() throws Exception {
        Map<String, String> attributes = new HashMap<String, String>();
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        String connectorId = connectorManager.create(connectorDescription);

        connectorManager.delete(connectorId);

        try {
            serviceUtils.getService("(foo=bar)", 100L);
            fail("service should not be available anymore");
        } catch (OsgiServiceNotAvailableException e) {
            // expected
        }

        try {
            connectorManager.getAttributeValues(connectorId);
            fail("service was still in persistence after deletion");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testDeleteService_shouldNotExistAnymore() throws Exception {
        Map<String, String> attributes = new HashMap<String, String>();
        Map<String, Object> properties = new Hashtable<String, Object>();
        boolean connectorExists;
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        String connectorId = connectorManager.create(connectorDescription);

        connectorManager.delete(connectorId);

        connectorExists = connectorManager.connectorExists(connectorId);
        assertFalse("service was still existing after deletion", connectorExists);
    }

    @Test
    public void testGetXLinkRegistration_isEmptyOnInitial() {
        String hostId = "127.0.0.1";
        assertTrue(connectorManager.getXLinkRegistrations(hostId).isEmpty());
    }

    @Test
    public void testGetXLinkRegistration_returnsConnectedRegistration() {
        String connectorId = "test+test+test";
        String hostId = "127.0.0.1";
        String toolName = "myTool";
        ModelViewMapping[] modelViewMappings = createModelViewsMappings(toolName);
        connectorManager.registerWithXLink(connectorId, hostId, toolName, modelViewMappings);
        List<XLinkConnectorRegistration> registrations = connectorManager.getXLinkRegistrations(hostId);
        assertEquals(1, registrations.size());
        assertThat(registrations.get(0).getHostId(), is(hostId));
        assertThat(registrations.get(0).getConnectorId(), is(connectorId));
        assertThat(registrations.get(0).getToolName(), is(toolName));
    }

    @Test
    public void testUnregisterFromXLink_OnMissingRegistration_NoFail() {
        String connectorId = "test+test+test";
        connectorManager.unregisterFromXLink(connectorId);
    }

    @Test
    public void testUnregisterFromXLink_isEmptyAfterDisconnect() {
        String connectorId = "test+test+test";
        String hostId = "127.0.0.1";
        String toolName = "myTool";
        ModelViewMapping[] modelViewMappings = createModelViewsMappings(toolName);
        connectorManager.registerWithXLink(connectorId, hostId, toolName, modelViewMappings);
        connectorManager.unregisterFromXLink(connectorId);
        assertTrue(((XLinkConnectorManager) connectorManager).getXLinkRegistrations(hostId).isEmpty());
    }

    // @Test(expected = DomainNotLinkableException.class)
    // public void testConnectorValidation_connectorIsNullAndFails() {
    // String connectorId = "test3+test3+test3";
    // String hostId = "127.0.0.1";
    // String toolName = "myTool";
    // ModelViewMapping[] modelViewMappings
    // = createModelViewsMappings(toolName);
    // connectorManager.registerForXLink(connectorId, hostId, toolName, modelViewMappings);
    // }
    //
    // @Test(expected = DomainNotLinkableException.class)
    // public void testConnectorValidation_connectorIsNotLinkableAndFails() {
    // String connectorId = "test4+test4+test4";
    // String hostId = "127.0.0.1";
    // String toolName = "myTool";
    // ModelViewMapping[] modelViewMappings
    // = createModelViewsMappings(toolName);
    // connectorManager.registerForXLink(connectorId, hostId, toolName, modelViewMappings);
    // }

    private ModelViewMapping[] createModelViewsMappings(String toolName) {
        String viewId1 = "exampleViewId_1";
        String viewId2 = "exampleViewId_2";
        ModelViewMapping[] modelViewMappings = new ModelViewMapping[1];
        Map<Locale, String> descriptions = new HashMap<>();
        List<XLinkConnectorView> views = new ArrayList<XLinkConnectorView>();

        descriptions.put(Locale.ENGLISH, "This is a demo view.");
        descriptions.put(Locale.GERMAN, "Das ist eine demonstration view.");
        views = new ArrayList<XLinkConnectorView>();
        views.add(new XLinkConnectorView(viewId1, toolName, descriptions));
        views.add(new XLinkConnectorView(viewId2, toolName, descriptions));

        modelViewMappings[0] =
            new ModelViewMapping(
                new ModelDescription(
                    ExampleObjectOrientedModel.class.getName(),
                    "3.0.0.SNAPSHOT")
                , views.toArray(new XLinkConnectorView[0]));
        return modelViewMappings;
    }

    @Test
    public void testCreateConnectorWithSkipDomainType_shouldNotInvokeSetDomainType() throws Exception {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(Constants.SKIP_SET_DOMAIN_TYPE, "true");
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        NullDomainImpl mock2 = mock(NullDomainImpl.class);
        when(factory.createNewInstance(anyString())).thenReturn(mock2);
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);
        connectorManager.create(connectorDescription);
        verify(mock2, never()).setDomainId(anyString());
        verify(mock2, never()).setConnectorId(anyString());
    }

    @Test
    public void testCreateConnectorWithToolModel_shouldCreateTransformingProxy() throws Exception {
        NullConnector mockedConnector = mock(NullConnector.class);
        when(factory.createNewInstance(anyString())).thenReturn(mockedConnector);

        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("answer", "42");
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        connectorManager.create(connectorDescription);

        NullDomain service = (NullDomain) serviceUtils.getService("(foo=bar)", 100L);
        service.nullMethod();
        verify(mockedConnector).nullMethod();
    }

    @Test
    public void testCallTransformingProxy_shouldTransformArguments() throws Exception {
        NullConnector mockedConnector = mock(NullConnector.class);
        when(factory.createNewInstance(anyString())).thenReturn(mockedConnector);

        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("answer", "42");
        Map<String, Object> properties = new Hashtable<String, Object>();
        properties.put("foo", "bar");
        ConnectorDescription connectorDescription = new ConnectorDescription("test", "testc", attributes, properties);

        connectorManager.create(connectorDescription);

        NullDomain service = (NullDomain) serviceUtils.getService("(foo=bar)", 100L);
        DummyModel dummyModel = new DummyModel();
        dummyModel.setId("42");
        dummyModel.setValue("foo");

        NullModel nullModel = new NullModel();
        nullModel.setId(42);
        nullModel.setValue("foo");
        when(transformationEngine.performTransformation(
            any(ModelDescription.class), any(ModelDescription.class), eq(dummyModel)))
            .thenReturn(nullModel);

        service.commitModel(dummyModel);
        verify(mockedConnector).commitModel(nullModel);
    }

    @SuppressWarnings("unchecked")
    private void registerMockedFactory() throws Exception {
        factory = mock(ConnectorInstanceFactory.class);
        when(factory.createNewInstance(anyString())).thenReturn(new NullDomainImpl());
        when(factory.applyAttributes(any(Connector.class), anyMap())).thenAnswer(new Answer<Connector>() {
            @Override
            public Connector answer(InvocationOnMock invocation) throws Throwable {
                return (Connector) invocation.getArguments()[0];
            }
        });
        Hashtable<String, Object> factoryProps = new Hashtable<String, Object>();
        factoryProps.put(Constants.CONNECTOR_KEY, "testc");
        factoryProps.put(Constants.DOMAIN_KEY, "test");
        registerService(factory, factoryProps, ConnectorInstanceFactory.class);
    }

    private void registerMockedDomainProvider() {
        DomainProvider domainProvider = mock(DomainProvider.class);
        when(domainProvider.getDomainInterface()).thenAnswer(new Answer<Class<? extends Domain>>() {
            @Override
            public Class<? extends Domain> answer(InvocationOnMock invocation) throws Throwable {
                return NullDomain.class;
            }
        });
        Hashtable<String, Object> domainProviderProps = new Hashtable<String, Object>();
        domainProviderProps.put("domain", "test");
        registerService(domainProvider, domainProviderProps, DomainProvider.class);
    }

}
