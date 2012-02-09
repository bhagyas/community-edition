/*
 * Copyright (C) 2005-2011 Alfresco Software Limited.
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
package org.alfresco.filesys.repo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

import org.alfresco.filesys.alfresco.ExtendedDiskInterface;
import org.alfresco.filesys.config.ServerConfigurationBean;
import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.core.DeviceContext;
import org.alfresco.jlan.server.core.DeviceContextException;
import org.alfresco.jlan.server.filesys.FileAccessToken;
import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.FileOpenParams;
import org.alfresco.jlan.server.filesys.FileStatus;
import org.alfresco.jlan.server.filesys.NetworkFile;
import org.alfresco.jlan.server.filesys.SearchContext;
import org.alfresco.jlan.server.filesys.TreeConnection;
import org.alfresco.jlan.server.filesys.cache.FileState;
import org.alfresco.jlan.server.filesys.cache.FileStateCache;
import org.alfresco.jlan.server.filesys.cache.NetworkFileStateInterface;
import org.alfresco.jlan.server.filesys.pseudo.PseudoFile;
import org.alfresco.jlan.smb.SharingMode;
import org.alfresco.model.ContentModel;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.config.ConfigElement;

/**
 * The Legacy file state driver is used to update JLAN's file state cache.
 * <p>
 * This class decorates an ExtendedDiskInterface with odds and ends to keep JLan happy.  
 * <p>
 * In particular this implementation cannot contain any code that requires access to the 
 * alfresco repository.
 * 
 */
public class LegacyFileStateDriver implements ExtendedDiskInterface
{
    private ExtendedDiskInterface diskInterface;
          
    public void init()
    {
        PropertyCheck.mandatory(this, "diskInterface", diskInterface);
    }
    
    private static final Log logger = LogFactory.getLog(LegacyFileStateDriver.class);

    @Override
    public void treeOpened(SrvSession sess, TreeConnection tree)
    {
        diskInterface.treeOpened(sess, tree);
        
    }

    @Override
    public void treeClosed(SrvSession sess, TreeConnection tree)
    {
        diskInterface.treeClosed(sess, tree);
    }

    @Override
    public NetworkFile createFile(SrvSession sess, TreeConnection tree,
            FileOpenParams params) throws IOException
    {
        ContentContext tctx = (ContentContext) tree.getContext();
        
        FileAccessToken token = null;
        
        if(tctx.hasStateCache())
        {
            FileStateCache cache = tctx.getStateCache();
            FileState fstate = tctx.getStateCache().findFileState( params.getPath(), true);
            token = cache.grantFileAccess(params, fstate, FileStatus.NotExist);
            if(logger.isDebugEnabled())
            {
                logger.debug("create file created lock token:" + token);
            }
        }
        
        try
        {
            NetworkFile newFile = diskInterface.createFile(sess, tree, params);
            
            newFile.setAccessToken(token);
          
            if(tctx.hasStateCache())
            {
                FileState fstate = tctx.getStateCache().findFileState( params.getPath(), true);
                fstate.setProcessId(params.getProcessId());
                fstate.setSharedAccess( params.getSharedAccess());
            
                // Indicate that the file is open
                fstate.setFileStatus(newFile.isDirectory()? FileStatus.DirectoryExists : FileStatus.FileExists);
         
                long allocationSize = params.getAllocationSize();
                if(allocationSize > 0)
                {
                    fstate.setAllocationSize(allocationSize);
                    fstate.setFileSize(allocationSize);
                }
                
                if (newFile instanceof NodeRefNetworkFile)
                {
                    NodeRefNetworkFile x = (NodeRefNetworkFile)newFile;
                    x.setFileState(fstate);
                }
                
                if (newFile instanceof TempNetworkFile)
                {
                    TempNetworkFile x = (TempNetworkFile)newFile;
                    x.setFileState(fstate);
                }   
            }
            
            if (newFile instanceof NodeRefNetworkFile)
            {
                NodeRefNetworkFile x = (NodeRefNetworkFile)newFile;
                x.setProcessId( params.getProcessId());
                x.setAccessToken(token);
            }
            
            if (newFile instanceof TempNetworkFile)
            {
                TempNetworkFile x = (TempNetworkFile)newFile;
                x.setAccessToken(token);
            }
            
            return newFile;
            
        }
        catch(IOException ie)
        {
            if(logger.isDebugEnabled())
            {
                logger.debug("create file exception caught", ie);   
            }    
            if(tctx.hasStateCache() && token != null)
            {
                FileStateCache cache = tctx.getStateCache();
                FileState fstate = tctx.getStateCache().findFileState( params.getPath(), false);
                if(fstate != null && token != null)
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("create file release lock token:" + token);
                    }
                    cache.releaseFileAccess(fstate, token);
                }
            }
            throw ie;
        }
    }

    @Override
    public NetworkFile openFile(SrvSession sess, TreeConnection tree,
            FileOpenParams params) throws IOException
    {
        ContentContext tctx = (ContentContext) tree.getContext();
        String path = params.getPath();

        FileAccessToken token = null;

        if(tctx.hasStateCache())
        {
            if(!params.isDirectory())
            {
                FileStateCache cache = tctx.getStateCache();
                FileState fstate = tctx.getStateCache().findFileState( params.getPath(), true);
                token = cache.grantFileAccess(params, fstate, FileStatus.Unknown);
                if(logger.isDebugEnabled())
                {
                    logger.debug("open file created lock token:" + token);
                }
            }
        }

        try
        {
            NetworkFile openFile = diskInterface.openFile(sess, tree, params);
            
            openFile.setAccessToken(token);
            
            FileState fstate = null;
            
            if(tctx.hasStateCache())
            {
                fstate = tctx.getStateCache().findFileState( path, true);
                fstate.setProcessId(params.getProcessId());
                fstate.setSharedAccess( params.getSharedAccess());
                
                // Access date time is read/write time not open time
                // fstate.updateAccessDateTime();
                
                fstate.setFileSize(openFile.getFileSize());
                fstate.updateChangeDateTime(openFile.getModifyDate());
                fstate.updateModifyDateTime(openFile.getModifyDate());
            }
            
            if (openFile instanceof ContentNetworkFile)
            {
                ContentNetworkFile x = (ContentNetworkFile)openFile;
                x.setProcessId( params.getProcessId());

                if(fstate != null)
                {
                    x.setFileState(fstate);
                    fstate.setFileStatus(FileStatus.FileExists);
                }
            } 
            else if (openFile instanceof TempNetworkFile)
            {
                TempNetworkFile x = (TempNetworkFile)openFile;
                if(fstate != null)
                {
                    x.setFileState(fstate);
                    fstate.setFileStatus(FileStatus.FileExists);
                }
            }
            else if (openFile instanceof AlfrescoFolder)
            {
                AlfrescoFolder x = (AlfrescoFolder)openFile;
                if(fstate != null)
                {
                    x.setFileState(fstate);
                    fstate.setFileStatus(FileStatus.DirectoryExists);
                }
            }
            else if (openFile instanceof NetworkFile)
            {
                NetworkFile x = (NetworkFile)openFile;
                if(fstate != null)
                {
                    // NetworkFile does not have setFileState
                    //x.setFileState(fstate);
                    fstate.setFileStatus(FileStatus.FileExists);
                }           
            }

            return openFile;
        }
        catch(IOException ie)
        {
            if(logger.isDebugEnabled())
            {
                logger.debug("open file exception caught", ie);   
            }  
            if(tctx.hasStateCache() && token != null)
            {
                FileStateCache cache = tctx.getStateCache();
                FileState fstate = tctx.getStateCache().findFileState( params.getPath(), false);
                if(fstate != null)
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("open file release lock token:" + token);
                    }
                    cache.releaseFileAccess(fstate, token);
                }
            }
            throw ie;
        }

    }
    
    @Override
    public void closeFile(SrvSession sess, TreeConnection tree,
            NetworkFile param) throws IOException
    {
        ContentContext tctx = (ContentContext) tree.getContext();
        
        if(logger.isDebugEnabled())
        {
            logger.debug("closeFile:" + param.getFullName() + ", accessToken:" + param.getAccessToken());
        }
        
        try
        {
            diskInterface.closeFile(sess, tree, param);

            if(tctx.hasStateCache())
            {
                FileStateCache cache = tctx.getStateCache();
                FileState fstate = cache.findFileState( param.getFullName(), true);
                
                if(fstate.getOpenCount() ==0 )
                {
                    logger.debug("OpenCount = 0, reset shared access to READ WRITE DELETE");
                    fstate.setSharedAccess( SharingMode.READWRITE + SharingMode.DELETE);
                    
                    fstate.setAllocationSize(-1);
                    fstate.setFileSize(-1);
                    fstate.updateChangeDateTime(0);
                    fstate.updateModifyDateTime(0);    
                }
                
                if(fstate != null && param.getAccessToken() != null)
                {

                    FileAccessToken token = param.getAccessToken();
                    if(logger.isDebugEnabled() && token != null)
                    {
                        logger.debug("close file release access token:" + token);
                    }
                    cache.releaseFileAccess(fstate, token);
                }
            }
        }
        catch(IOException ie)
        {
            if(logger.isDebugEnabled())
            {
                logger.debug("close file exception caught", ie);   
            }  
            throw ie;
        }    
    }

    @Override
    public void registerContext(DeviceContext ctx) throws DeviceContextException
    {
        diskInterface.registerContext(ctx);
    }
    
    public void setDiskInterface(ExtendedDiskInterface diskInterface)
    {
        this.diskInterface = diskInterface;
    }

    public ExtendedDiskInterface getDiskInterface()
    {
        return diskInterface;
    }

    @Override
    public void createDirectory(SrvSession sess, TreeConnection tree,
            FileOpenParams params) throws IOException
    {
        diskInterface.createDirectory(sess, tree, params);
    }

    @Override
    public void deleteDirectory(SrvSession sess, TreeConnection tree, String dir)
            throws IOException
    {
        diskInterface.deleteDirectory(sess, tree, dir);
    }

    @Override
    public void deleteFile(SrvSession sess, TreeConnection tree, String name)
            throws IOException
    {
        diskInterface.deleteFile(sess, tree, name);
    }

    @Override
    public int fileExists(SrvSession sess, TreeConnection tree, String name)
    {
        return diskInterface.fileExists(sess, tree, name);
    }

    @Override
    public void flushFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws IOException
    {
        diskInterface.flushFile(sess, tree, file);        
    }

    @Override
    public FileInfo getFileInformation(SrvSession sess, TreeConnection tree,
            String name) throws IOException
    {
        return diskInterface.getFileInformation(sess, tree, name);
    }

    @Override
    public boolean isReadOnly(SrvSession sess, DeviceContext ctx)
            throws IOException
    {
        return diskInterface.isReadOnly(sess, ctx);
    }

    @Override
    public int readFile(SrvSession sess, TreeConnection tree, NetworkFile file,
            byte[] buf, int bufPos, int siz, long filePos) throws IOException
    {
        return diskInterface.readFile(sess, tree, file, buf, bufPos, siz, filePos);
    }

    @Override
    public void renameFile(SrvSession sess, TreeConnection tree,
            String oldName, String newName) throws IOException
    {
        diskInterface.renameFile(sess, tree, oldName, newName);        
    }

    @Override
    public long seekFile(SrvSession sess, TreeConnection tree,
            NetworkFile file, long pos, int typ) throws IOException
    {
        return diskInterface.seekFile(sess, tree, file, pos, typ);
    }

    @Override
    public void setFileInformation(SrvSession sess, TreeConnection tree,
            String name, FileInfo info) throws IOException
    {

       diskInterface.setFileInformation(sess, tree, name, info);
        
       ContentContext tctx = (ContentContext) tree.getContext();
        
       if(tctx.hasStateCache())
       {
           FileStateCache cache = tctx.getStateCache();
           FileState fstate = cache.findFileState( name, true);
 
//           if ( info.hasSetFlag(FileInfo.SetCreationDate))
//           {
//               if ( logger.isDebugEnabled())
//               {
//                   logger.debug("Set creation date in file state cache" + name + ", " + info.getCreationDateTime());
//               }
//               Date createDate = new Date( info.getCreationDateTime());
//               fstate.u(createDate.getTime()); 
//           }
           if ( info.hasSetFlag(FileInfo.SetModifyDate)) 
           {   
               if ( logger.isDebugEnabled())
               {
                   logger.debug("Set modification date in file state cache" + name + ", " + info.getModifyDateTime());
               }
               Date modifyDate = new Date( info.getModifyDateTime());
               fstate.updateModifyDateTime(modifyDate.getTime()); 
           }
       }        
    }

    @Override
    public SearchContext startSearch(SrvSession sess, TreeConnection tree,
            String searchPath, int attrib) throws FileNotFoundException
    {
        return diskInterface.startSearch(sess, tree, searchPath, attrib);

    }

    @Override
    public void truncateFile(SrvSession sess, TreeConnection tree,
            NetworkFile file, long siz) throws IOException
    {
        diskInterface.truncateFile(sess, tree, file, siz);
    }

    @Override
    public int writeFile(SrvSession sess, TreeConnection tree,
            NetworkFile file, byte[] buf, int bufoff, int siz, long fileoff)
            throws IOException
    {
        return diskInterface.writeFile(sess, tree, file, buf, bufoff, siz, fileoff);
    }

    @Override
    public DeviceContext createContext(String shareName, ConfigElement args)
            throws DeviceContextException
    {
        
        return diskInterface.createContext(shareName, args);
    }
}
  