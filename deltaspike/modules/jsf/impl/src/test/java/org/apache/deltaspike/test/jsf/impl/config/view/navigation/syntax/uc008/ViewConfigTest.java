/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.deltaspike.test.jsf.impl.config.view.navigation.syntax.uc008;

import org.apache.deltaspike.core.api.config.view.metadata.ConfigDescriptor;
import org.apache.deltaspike.core.api.config.view.metadata.ViewConfigDescriptor;
import org.apache.deltaspike.core.api.config.view.metadata.ViewConfigResolver;
import org.apache.deltaspike.core.spi.config.view.ViewConfigNode;
import org.apache.deltaspike.jsf.api.config.view.Folder;
import org.apache.deltaspike.jsf.api.config.view.View;
import org.apache.deltaspike.jsf.impl.config.view.ViewConfigExtension;
import org.apache.deltaspike.jsf.impl.config.view.ViewConfigResolverProducer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ViewConfigTest
{
    private ViewConfigExtension viewConfigExtension;

    private ViewConfigResolverProducer viewConfigResolverProducer;

    @Before
    public void before()
    {
        this.viewConfigExtension = new ViewConfigExtension();
        this.viewConfigResolverProducer = new ViewConfigResolverProducer(this.viewConfigExtension);
    }

    @After
    public void after()
    {
        this.viewConfigExtension.freeViewConfigCache(null);
    }

    @Test
    public void testMetaDataInheritanceInTree()
    {
        this.viewConfigExtension.addPageDefinition(Pages.class);
        this.viewConfigExtension.addPageDefinition(Pages.Admin.class);
        this.viewConfigExtension.addPageDefinition(Pages.Admin.Index.class);

        ViewConfigNode node = this.viewConfigExtension.findNode(Pages.class);

        Assert.assertNotNull(node);
        Assert.assertNotNull(node.getParent()); //Root
        Assert.assertNull(node.getParent().getParent());

        Assert.assertNotNull(node.getChildren());
        Assert.assertEquals(1, node.getChildren().size());

        Assert.assertNotNull(node.getMetaData());
        Assert.assertEquals(1, node.getMetaData().size());
        Assert.assertEquals(".", ((Folder) node.getMetaData().iterator().next()).name());

        Assert.assertNotNull(node.getInheritedMetaData());
        Assert.assertEquals(0, node.getInheritedMetaData().size());


        node = this.viewConfigExtension.findNode(Pages.Admin.class);

        Assert.assertNotNull(node);
        Assert.assertNotNull(node.getParent()); //Admin
        Assert.assertNotNull(node.getParent().getParent()); //Root
        Assert.assertNull(node.getParent().getParent().getParent());

        Assert.assertNotNull(node.getChildren());
        Assert.assertEquals(1, node.getChildren().size());

        Assert.assertNotNull(node.getMetaData());
        Assert.assertEquals(1, node.getMetaData().size());
        Assert.assertEquals(Folder.class, node.getMetaData().iterator().next().annotationType());

        Assert.assertNotNull(node.getInheritedMetaData());
        Assert.assertEquals(0, node.getInheritedMetaData().size());


        node = this.viewConfigExtension.findNode(Pages.Admin.Index.class);

        Assert.assertNotNull(node);
        Assert.assertNotNull(node.getParent()); //Admin
        Assert.assertNotNull(node.getParent().getParent()); //Pages
        Assert.assertNotNull(node.getParent().getParent().getParent()); //Root
        Assert.assertNull(node.getParent().getParent().getParent().getParent());

        Assert.assertNotNull(node.getChildren());
        Assert.assertEquals(0, node.getChildren().size());

        Assert.assertNotNull(node.getMetaData());
        Assert.assertEquals(1, node.getMetaData().size());
        Assert.assertEquals(View.NavigationMode.DEFAULT, ((View) node.getMetaData().iterator().next()).navigation());
        Assert.assertEquals(View.ViewParameterMode.DEFAULT, ((View) node.getMetaData().iterator().next()).viewParams());
        Assert.assertEquals("", ((View) node.getMetaData().iterator().next()).name());
        Assert.assertEquals(View.Extension.DEFAULT, ((View) node.getMetaData().iterator().next()).extension());

        Assert.assertNotNull(node.getInheritedMetaData());
        Assert.assertEquals(0, node.getInheritedMetaData().size());


        Assert.assertNotNull(node.getInheritedMetaData());
        Assert.assertEquals(0, node.getInheritedMetaData().size());
    }

    @Test
    public void testMetaDataInheritanceInViewConfig()
    {
        this.viewConfigExtension.addPageDefinition(Pages.class);
        this.viewConfigExtension.addPageDefinition(Pages.Admin.class);
        this.viewConfigExtension.addPageDefinition(Pages.Admin.Index.class);

        ViewConfigResolver viewConfigResolver = this.viewConfigResolverProducer.createViewConfigResolver();
        ConfigDescriptor<?> configDescriptor = viewConfigResolver.getConfigDescriptor(Pages.class);

        Assert.assertNotNull(configDescriptor);
        Assert.assertNotNull(configDescriptor.getConfigClass());
        Assert.assertEquals(Pages.class, configDescriptor.getConfigClass());

        Assert.assertNotNull(configDescriptor.getMetaData());
        Assert.assertEquals(1, configDescriptor.getMetaData().size());
        Assert.assertEquals(1, configDescriptor.getMetaData(Folder.class).size());
        Assert.assertEquals("/pages/", configDescriptor.getMetaData(Folder.class).iterator().next().name());


        configDescriptor = viewConfigResolver.getConfigDescriptor(Pages.Admin.class);

        Assert.assertNotNull(configDescriptor);
        Assert.assertNotNull(configDescriptor.getConfigClass());
        Assert.assertEquals(Pages.Admin.class, configDescriptor.getConfigClass());

        Assert.assertNotNull(configDescriptor.getMetaData());
        Assert.assertEquals(1, configDescriptor.getMetaData().size());
        Assert.assertEquals(1, configDescriptor.getMetaData(Folder.class).size());
        Assert.assertEquals("/pages/admin/", configDescriptor.getMetaData(Folder.class).iterator().next().name());


        ViewConfigDescriptor viewConfigDescriptor = viewConfigResolver.getViewConfigDescriptor(Pages.Admin.Index.class);

        Assert.assertNotNull(viewConfigDescriptor);
        Assert.assertEquals("/pages/admin/index.xhtml", viewConfigDescriptor.getViewId());
        Assert.assertEquals(Pages.Admin.Index.class, viewConfigDescriptor.getConfigClass());

        Assert.assertNotNull(viewConfigDescriptor.getMetaData());
        Assert.assertEquals(View.NavigationMode.FORWARD, viewConfigDescriptor.getMetaData(View.class).iterator().next().navigation());
        Assert.assertEquals(View.ViewParameterMode.EXCLUDE, viewConfigDescriptor.getMetaData(View.class).iterator().next().viewParams());
        Assert.assertEquals("index", viewConfigDescriptor.getMetaData(View.class).iterator().next().name());
        Assert.assertEquals("xhtml", viewConfigDescriptor.getMetaData(View.class).iterator().next().extension());
    }
}