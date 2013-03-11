/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.drive.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.runtime.model.ContributionFragmentRegistry;

/**
 * Registry for {@code fileSystemItemFactory} contributions.
 *
 * @author Antoine Taillefer
 * @see FileSystemItemAdapterServiceImpl
 */
public class FileSystemItemFactoryRegistry extends
        ContributionFragmentRegistry<FileSystemItemFactoryDescriptor> {

    private static final Log log = LogFactory.getLog(FileSystemItemFactoryRegistry.class);

    protected final Map<String, FileSystemItemFactoryDescriptor> factoryDescriptors = new HashMap<String, FileSystemItemFactoryDescriptor>();

    @Override
    public String getContributionId(FileSystemItemFactoryDescriptor contrib) {
        String name = contrib.getName();
        if (StringUtils.isEmpty(name)) {
            throw new ClientRuntimeException(
                    "Cannot register fileSystemItemFactory without a name.");
        }
        return name;
    }

    @Override
    public void contributionUpdated(String id,
            FileSystemItemFactoryDescriptor contrib,
            FileSystemItemFactoryDescriptor newOrigContrib) {
        if (newOrigContrib.isEnabled()) {
            // No merge
            log.trace(String.format(
                    "Putting contribution %s with id %s in factory descriptors",
                    newOrigContrib, id));
            factoryDescriptors.put(id, newOrigContrib);
        } else {
            log.trace(String.format(
                    "Removing disabled contribution with id %s from factory descriptors",
                    id));
            factoryDescriptors.remove(id);
        }
    }

    @Override
    public void contributionRemoved(String id,
            FileSystemItemFactoryDescriptor origContrib) {
        log.trace(String.format(
                "Removing contribution with id %s from factory descriptors", id));
        factoryDescriptors.remove(id);
    }

    @Override
    public FileSystemItemFactoryDescriptor clone(
            FileSystemItemFactoryDescriptor orig) {
        log.trace(String.format("Cloning contribution with id %s",
                orig.getName()));
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(orig);
            ByteArrayInputStream bis = new ByteArrayInputStream(
                    bos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bis);
            return (FileSystemItemFactoryDescriptor) ois.readObject();
        } catch (Exception e) {
            throw new ClientRuntimeException("Cannot clone contribution "
                    + orig, e);
        }
    }

    @Override
    public void merge(FileSystemItemFactoryDescriptor src,
            FileSystemItemFactoryDescriptor dst) {
        // Null merge
        log.trace(String.format(
                "Null merge between contributions %s (source) and %s (destination)",
                src.getName(), dst.getName()));
    }

    protected List<FileSystemItemFactoryWrapper> getOrderedFactories()
            throws Exception {
        List<FileSystemItemFactoryWrapper> factories = new ArrayList<FileSystemItemFactoryWrapper>();
        List<FileSystemItemFactoryDescriptor> orderedFactoryDescriptors = new ArrayList<FileSystemItemFactoryDescriptor>(
                factoryDescriptors.values());
        Collections.sort(orderedFactoryDescriptors);
        for (FileSystemItemFactoryDescriptor factoryDesc : orderedFactoryDescriptors) {
            FileSystemItemFactoryWrapper factoryWrapper = new FileSystemItemFactoryWrapper(
                    factoryDesc.getDocType(), factoryDesc.getFacet(),
                    factoryDesc.getFactory());
            factories.add(factoryWrapper);
        }
        return factories;
    }

}
