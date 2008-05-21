/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.changelog;


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerAttribute;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.invocation.InvocationStack;
import org.apache.directory.server.core.jndi.ServerContext;
import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.ldif.ChangeType;
import org.apache.directory.shared.ldap.ldif.LdifEntry;
import org.apache.directory.shared.ldap.ldif.LdifUtils;
import org.apache.directory.shared.ldap.util.Base64;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;


/**
 * An interceptor which maintains a change LOG as it intercepts changes to the
 * directory.  It mainains a changes.LOG file using the LDIF format for changes.
 * It appends changes to this file so the entire LDIF file can be loaded to 
 * replicate the state of the server.
 * 
 */
public class OriginalChangeLogInterceptor extends BaseInterceptor implements Runnable
{
    /** logger used by this class */
    private static final Logger LOG = LoggerFactory.getLogger( OriginalChangeLogInterceptor.class );

    /** time to wait before automatically waking up the writer thread */
    private static final long WAIT_TIMEOUT_MILLIS = 1000;
    
    /** the changes.LOG file's stream which we append change LOG messages to */
    private PrintWriter out;
    
    /** queue of string buffers awaiting serialization to the LOG file */
    private final Queue<StringBuilder> queue = new LinkedList<StringBuilder>();
    
    /** a handle on the attributeType registry to determine the binary nature of attributes */
    private AttributeTypeRegistry atRegistry;
    
    /** determines if this service has been activated */
    private boolean isActive;
    
    /** thread used to asynchronously write change logs to disk */
    private Thread writer;
    
    
    // -----------------------------------------------------------------------
    // Overridden init() and destroy() methods
    // -----------------------------------------------------------------------

    
    public void init( DirectoryService directoryService ) throws Exception
    {
        super.init( directoryService );

        // Get a handle on the attribute registry to check if attributes are binary
        atRegistry = directoryService.getRegistries().getAttributeTypeRegistry();

        // Open a print stream to use for flushing LDIFs into
        File changes = new File( directoryService.getWorkingDirectory(), "changes.LOG" );
        
        try
        {
            if ( changes.exists() )
            {
                out = new PrintWriter( new FileWriter( changes, true ) );
            }
            else
            {
                out = new PrintWriter( new FileWriter( changes ) );
            }
        }
        catch( Exception e )
        {
            LOG.error( "Failed to open the change LOG file: " + changes, e );
        }
        
        out.println( "# -----------------------------------------------------------------------------" );
        out.println( "# Initializing changelog service: " + DateUtils.getGeneralizedTime() );
        out.println( "# -----------------------------------------------------------------------------" );
        out.flush();
        
        writer = new Thread( this );
        isActive = true;
        writer.start();
    }
    
    
    public void destroy()
    {
        // Gracefully stop writer thread and push remaining enqueued buffers ourselves
        isActive = false;
        
        do
        {
            // Let's notify the writer thread to make it die faster
            synchronized( queue )
            {
                queue.notifyAll();
            }
            
            // Sleep tiny bit waiting for the writer to die
            try
            {
                Thread.sleep( 50 );
            }
            catch ( InterruptedException e )
            {
                LOG.error( "Failed to sleep while waiting for writer to die", e );
            }
        } while ( writer.isAlive() );
        
        // Ok lock down queue and start draining it
        synchronized( queue )
        {
            while ( ! queue.isEmpty() )
            {
                StringBuilder buf = queue.poll();
                
                if ( buf != null )
                {
                    out.println( buf );
                }
            }
        }

        // Print message that we're stopping LOG service, flush and close
        out.println( "# -----------------------------------------------------------------------------" );
        out.println( "# Deactivating changelog service: " + DateUtils.getGeneralizedTime() );
        out.println( "# -----------------------------------------------------------------------------" );
        out.flush();
        out.close();
        
        super.destroy();
    }
    
    
    // -----------------------------------------------------------------------
    // Implementation for Runnable.run() for writer Thread
    // -----------------------------------------------------------------------

    
    public void run()
    {
        while ( isActive )
        {
            StringBuilder buf;

            // Grab semphore to queue and dequeue from it
            synchronized( queue )
            {
                try 
                { 
                    queue.wait( WAIT_TIMEOUT_MILLIS ); 
                } 
                catch ( InterruptedException e ) 
                { 
                    LOG.error( "Failed to to wait() on queue", e );
                }
                
                buf = queue.poll();
                queue.notifyAll();
            }
            
            // Do writing outside of synch block to allow other threads to enqueue
            if ( buf != null )
            {
                out.println( buf );
                out.flush();
            }
        }
    }
    
    
    // -----------------------------------------------------------------------
    // Overridden (only change inducing) intercepted methods
    // -----------------------------------------------------------------------

    public void add( NextInterceptor next, AddOperationContext opContext ) throws Exception
    {
        StringBuilder buf;
        next.add( opContext );
        
        if ( ! isActive )
        {
            return;
        }
        
        // Append comments that can be used to track the user and time this operation occurred
        buf = new StringBuilder();
        buf.append( "\n#! creatorsName: " );
        buf.append( getPrincipalName() );
        buf.append( "\n#! createTimestamp: " );
        buf.append( DateUtils.getGeneralizedTime() );
        
        // Append the LDIF entry now
        buf.append( LdifUtils.convertToLdif( ServerEntryUtils.toAttributesImpl( opContext.getEntry() ) ) );

        // Enqueue the buffer onto a queue that is emptied by another thread asynchronously. 
        synchronized ( queue )
        {
            queue.offer( buf );
            queue.notifyAll();
        }
    }

    /**
     * The delete operation has to be stored with a way to restore the deleted element.
     * There is no way to do that but reading the entry and dump it into the LOG.
     */
    public void delete( NextInterceptor next, DeleteOperationContext opContext ) throws Exception
    {
        next.delete( opContext );

        if ( ! isActive )
        {
            return;
        }
        
        // Append comments that can be used to track the user and time this operation occurred
        StringBuilder buf = new StringBuilder();
        buf.append( "\n#! deletorsName: " );
        buf.append( getPrincipalName() );
        buf.append( "\n#! deleteTimestamp: " );
        buf.append( DateUtils.getGeneralizedTime() );
        
        LdifEntry entry = new LdifEntry();
        entry.setDn( opContext.getDn().getUpName() );
        entry.setChangeType( ChangeType.Delete );
        buf.append( LdifUtils.convertToLdif( entry ) );
        

        // Enqueue the buffer onto a queue that is emptied by another thread asynchronously. 
        synchronized ( queue )
        {
            queue.offer( buf );
            queue.notifyAll();
        }
    }

    
    public void modify( NextInterceptor next, ModifyOperationContext opContext ) throws Exception
    {
        StringBuilder buf;
        next.modify( opContext );

        if ( ! isActive )
        {
            return;
        }
        
        // Append comments that can be used to track the user and time this operation occurred
        buf = new StringBuilder();
        buf.append( "\n#! modifiersName: " );
        buf.append( getPrincipalName() );
        buf.append( "\n#! modifyTimestamp: " );
        buf.append( DateUtils.getGeneralizedTime() );
        
        // Append the LDIF record now
        buf.append( "\ndn: " );
        buf.append( opContext.getDn() );
        buf.append( "\nchangetype: modify" );

        List<Modification> mods = opContext.getModItems();
        
        for ( Modification mod :mods )
        {
            append( buf, (ServerAttribute)mod.getAttribute(), mod.getOperation().toString() + ": ");
        }
        
        buf.append( "\n" );

        // Enqueue the buffer onto a queue that is emptied by another thread asynchronously. 
        synchronized ( queue )
        {
            queue.offer( buf );
            queue.notifyAll();
        }
    }


    // -----------------------------------------------------------------------
    // Though part left as an exercise (Not Any More!)
    // -----------------------------------------------------------------------

    
    public void rename ( NextInterceptor next, RenameOperationContext renameContext ) throws Exception
    {
        next.rename( renameContext );
        
        if ( ! isActive )
        {
            return;
        }
        
        StringBuilder buf;
        
        // Append comments that can be used to track the user and time this operation occurred
        buf = new StringBuilder();
        buf.append( "\n#! principleName: " );
        buf.append( getPrincipalName() );
        buf.append( "\n#! operationTimestamp: " );
        buf.append( DateUtils.getGeneralizedTime() );
        
        // Append the LDIF record now
        buf.append( "\ndn: " );
        buf.append( renameContext.getDn() );
        buf.append( "\nchangetype: modrdn" );
        buf.append( "\nnewrdn: " ).append( renameContext.getNewRdn() );
        buf.append( "\ndeleteoldrdn: " ).append( renameContext.getDelOldDn() ? "1" : "0" );
        
        buf.append( "\n" );

        // Enqueue the buffer onto a queue that is emptied by another thread asynchronously. 
        synchronized ( queue )
        {
            queue.offer( buf );
            queue.notifyAll();
        }
    }

    
    public void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext moveAndRenameOperationContext )
        throws Exception
    {
        next.moveAndRename( moveAndRenameOperationContext );
        
        if ( ! isActive )
        {
            return;
        }
        
        StringBuilder buf;
        
        // Append comments that can be used to track the user and time this operation occurred
        buf = new StringBuilder();
        buf.append( "\n#! principleName: " );
        buf.append( getPrincipalName() );
        buf.append( "\n#! operationTimestamp: " );
        buf.append( DateUtils.getGeneralizedTime() );
        
        // Append the LDIF record now
        buf.append( "\ndn: " );
        buf.append( moveAndRenameOperationContext.getDn() );
        buf.append( "\nchangetype: modrdn" ); // FIXME: modrdn --> moddn ?
        buf.append( "\nnewrdn: " ).append( moveAndRenameOperationContext.getNewRdn() );
        buf.append( "\ndeleteoldrdn: " ).append( moveAndRenameOperationContext.getDelOldDn() ? "1" : "0" );
        buf.append( "\nnewsperior: " ).append( moveAndRenameOperationContext.getParent() );
        
        buf.append( "\n" );

        // Enqueue the buffer onto a queue that is emptied by another thread asynchronously. 
        synchronized ( queue )
        {
            queue.offer( buf );
            queue.notifyAll();
        }
    }

    
    public void move ( NextInterceptor next, MoveOperationContext moveOperationContext ) throws Exception
    {
        next.move( moveOperationContext );
        
        if ( ! isActive )
        {
            return;
        }
        
        StringBuilder buf;
        
        // Append comments that can be used to track the user and time this operation occurred
        buf = new StringBuilder();
        buf.append( "\n#! principleName: " );
        buf.append( getPrincipalName() );
        buf.append( "\n#! operationTimestamp: " );
        buf.append( DateUtils.getGeneralizedTime() );
        
        // Append the LDIF record now
        buf.append( "\ndn: " );
        buf.append( moveOperationContext.getDn() );
        buf.append( "\nchangetype: moddn" );
        buf.append( "\nnewsperior: " ).append( moveOperationContext.getParent() );
        
        buf.append( "\n" );

        // Enqueue the buffer onto a queue that is emptied by another thread asynchronously. 
        synchronized ( queue )
        {
            queue.offer( buf );
            queue.notifyAll();
        }
    }

    
    // -----------------------------------------------------------------------
    // Private utility methods used by interceptor methods
    // -----------------------------------------------------------------------

    
    /**
     * Appends an Attribute and its values to a buffer containing an LDIF entry taking
     * into account whether or not the attribute's syntax is binary or not.
     * 
     * @param buf the buffer written to and returned (for chaining)
     * @param attr the attribute written to the buffer
     * @return the buffer argument to allow for call chaining.
     * @throws Exception if the attribute is not identified by the registry
     */
    private StringBuilder append( StringBuilder buf, ServerAttribute attr ) throws Exception
    {
        String id = attr.getId();
        boolean isBinary = ! atRegistry.lookup( id ).getSyntax().isHumanReadable();
        
        if ( isBinary )
        {
            for ( Value<?> value:attr )
            {
                buf.append( "\n" );
                buf.append( id );
                buf.append( ":: " );
                String encoded;
                
                if ( value.get() instanceof String )
                {
                    encoded = ( String ) value.get();
                    
                    try
                    {
                        encoded = new String( Base64.encode( encoded.getBytes( "UTF-8" ) ) );
                    }
                    catch ( UnsupportedEncodingException e )
                    {
                        LOG.error( "can't convert to UTF-8: " + encoded, e );
                    }
                }
                else
                {
                    encoded = new String( Base64.encode( ( byte[] ) value.get() ) );
                }
                buf.append( encoded );
            }
        }
        else
        {
            for ( Value<?> value:attr )
            {
                buf.append( "\n" );
                buf.append( id );
                buf.append( ": " );
                buf.append( value.get() );
            }
        }
        
        return buf;
    }
    

    /**
     * Gets the DN of the user currently bound to the server executing this operation.  If 
     * the user is anonymous "" is returned.
     * 
     * @return the DN of the user executing the current intercepted operation
     * @throws Exception if we cannot access the interceptor stack
     */
    private String getPrincipalName() throws Exception
    {
        ServerContext ctx = ( ServerContext ) InvocationStack.getInstance().peek().getCaller();
        return ctx.getPrincipal().getName();
    }


    /**
     * Appends a modification delta instruction to an LDIF: i.e. 
     * <pre>
     * add: telephoneNumber
     * telephoneNumber: +1 408 555 1234
     * telephoneNumber: +1 408 444 9999
     * -
     * </pre>
     * 
     * @param buf the buffer to append the attribute delta to
     * @param mod the modified values if any for that attribute
     * @param modOp the modification operation as a string followd by ": "
     * @return the buffer argument provided for chaining
     * @throws Exception if the modification attribute id is undefined
     */
    private StringBuilder append( StringBuilder buf, ServerAttribute mod, String modOp ) throws Exception
    {
        buf.append( "\n" );
        buf.append( modOp );
        buf.append( mod.getId() );
        append( buf, mod );
        buf.append( "\n-" );
        return buf;
    }
}
