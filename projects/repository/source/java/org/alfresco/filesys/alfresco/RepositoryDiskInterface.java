/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.filesys.alfresco;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.alfresco.filesys.repo.OpenFileMode;
import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.filesys.NetworkFile;
import org.alfresco.jlan.server.filesys.TreeConnection;
import org.alfresco.service.cmr.repository.NodeRef;

/**
 * Extra methods for DiskInterface, primarily implemented to support CIFS shuffles.
 */
public interface RepositoryDiskInterface 
{
    /**
     * Copy the content from one node to another.
     * 
     * @param rootNode
     * @param fromPath - the source node
     * @param toPath - the target node
     * @throws FileNotFoundException 
     */
    public void copyContent(NodeRef rootNode, String fromPath, String toPath) throws FileNotFoundException;

    
    /**
     * CreateFile.
     * 
     * @param rootNode
     * @param fromPath - the source node
     * @param toPath - the target node
     * @param allocationSize size to allocate for new file
     * @throws FileNotFoundException 
     */
    public NetworkFile createFile(NodeRef rootNode, String Path, long allocationSize) throws IOException;

    /**
     * 
     * @param session // temp until refactor
     * @param tree // temp until refactor
     * @param rootNode
     * @param path
     * @param mode
     * @param truncate
     * @return NetworkFile
     */
    public NetworkFile openFile(SrvSession session, TreeConnection tree, NodeRef rootNode, String path, OpenFileMode mode, boolean truncate) throws IOException;

    /**
     * CloseFile.
     * 
     * @param session // temp until refactor
     * @param tree // temp until refactor
     * @param rootNode
     * @param fromPath - the source node
     * @param toPath - the target node
     * @throws FileNotFoundException 
     */
    public void closeFile(SrvSession session, TreeConnection tree, NodeRef rootNode, String Path, NetworkFile file) throws IOException;
    
    /**
     * 
     * @param session
     * @param tree
     * @param file
     */
    public void reduceQuota(SrvSession session, TreeConnection tree, NetworkFile file);
    
    /**
     * 
     * @param rootNode
     * @param path
     */
    public void deleteEmptyFile(NodeRef rootNode, String path);

}
