/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.core.schema.registries.synchronizers;


import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.name.RDN;
import org.apache.directory.shared.ldap.schema.MatchingRuleUse;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * A schema entity change handler for DitMatchingRuleUses.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class MatchingRuleUseSynchronizer extends AbstractRegistrySynchronizer
{
    /**
     * Creates a new instance of MatchingRuleUseSynchronizer.
     *
     * @param schemaManager The global schemaManager
     * @throws Exception If the initialization failed
     */
    protected MatchingRuleUseSynchronizer( SchemaManager schemaManager ) throws Exception
    {
        super( schemaManager );
        // TODO Auto-generated constructor stub
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean modify( ModifyOperationContext opContext, Entry targetEntry, 
        boolean cascade ) throws LdapException
    {
        // TODO Auto-generated method stub
        return SCHEMA_UNCHANGED;
    }


    /**
     * {@inheritDoc}
     */
    public void add( Entry entry ) throws LdapException
    {
        // TODO Auto-generated method stub
    }


    /**
     * {@inheritDoc}
     */
    public void delete( Entry entry, boolean cascade ) throws LdapException
    {
        // TODO Auto-generated method stub
    }


    public void moveAndRename( DN oriChildName, DN newParentName, RDN newRn, boolean deleteOldRn,
        Entry entry, boolean cascade ) throws LdapException
    {
        // TODO Auto-generated method stub
    }


    public void move( DN oriChildName, DN newParentName,
        Entry entry, boolean cascade ) throws LdapException
    {
        // TODO Auto-generated method stub
    }


    /**
     * {@inheritDoc}
     */
    public void rename( Entry entry, RDN newRdn, boolean cascade ) throws LdapException
    {
        // TODO Auto-generated method stub
    }


    public void add( MatchingRuleUse mru ) throws LdapException
    {
        // TODO Auto-generated method stub
    }


    public void delete( MatchingRuleUse mru, boolean cascade ) throws LdapException
    {
        // TODO Auto-generated method stub
    }
}
