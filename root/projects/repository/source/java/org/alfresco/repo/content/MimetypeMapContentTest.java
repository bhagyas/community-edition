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
package org.alfresco.repo.content;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import junit.framework.TestCase;

import org.alfresco.repo.content.filestore.FileContentReader;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.util.DataModelTestApplicationContextHelper;
import org.apache.poi.util.IOUtils;
import org.springframework.context.ApplicationContext;

/**
 * Content specific tests for MimeTypeMap
 * 
 * @see org.alfresco.repo.content.MimetypeMap
 * @see org.alfresco.repo.content.MimetypeMapTest
 */
public class MimetypeMapContentTest extends TestCase
{
    private static ApplicationContext ctx = DataModelTestApplicationContextHelper.getApplicationContext();
    
    private MimetypeService mimetypeService;
    
    @Override
    public void setUp() throws Exception
    {
        mimetypeService =  (MimetypeService)ctx.getBean("mimetypeService");
    }

    public void testGuessMimetypeForFile() throws Exception
    {
        // Correct ones
        assertEquals(
                "application/msword", 
                mimetypeService.guessMimetype("something.doc", openQuickTestFile("quick.doc"))
        );
        assertEquals(
                "application/msword", 
                mimetypeService.guessMimetype("SOMETHING.DOC", openQuickTestFile("quick.doc"))
        );
        
        // Incorrect ones, Tika spots the mistake
        assertEquals(
                "application/msword", 
                mimetypeService.guessMimetype("something.pdf", openQuickTestFile("quick.doc"))
        );
        assertEquals(
                "application/pdf", 
                mimetypeService.guessMimetype("something.doc", openQuickTestFile("quick.pdf"))
        );
        
        // Ones where we use a different mimetype to the canonical one
        assertEquals(
                "image/bmp", // Officially image/x-ms-bmp 
                mimetypeService.guessMimetype("image.bmp", openQuickTestFile("quick.bmp"))
        );

        
        // Where the file is corrupted
        File tmp = File.createTempFile("alfresco", ".tmp");
        ContentReader reader = openQuickTestFile("quick.doc");
        InputStream inp = reader.getContentInputStream();
        byte[] trunc = new byte[512+256];
        IOUtils.readFully(inp, trunc);
        inp.close();
        FileOutputStream out = new FileOutputStream(tmp);
        out.write(trunc);
        out.close();
        ContentReader truncReader = new FileContentReader(tmp);
        
        // Because the file is truncated, Tika won't be able to process the contents
        //  of the OLE2 structure
        // So, it'll fall back to just OLE2, but it won't fail
        assertEquals(
                "application/x-tika-msoffice", 
                mimetypeService.guessMimetype("something.doc", truncReader)
        );
    }
    
    private ContentReader openQuickTestFile(String filename)
    {
        URL url = getClass().getClassLoader().getResource("quick/" + filename);
        File file = new File(url.getFile());
        return new FileContentReader(file);
    }
}
