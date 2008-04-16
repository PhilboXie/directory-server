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
package org.apache.directory.server.core.jndi;


import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.integ.CiRunner;
import static org.apache.directory.server.core.integ.IntegrationUtils.getSystemContext;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import java.util.Hashtable;


/**
 * Contributed by Luke Taylor to fix DIRSERVER-169.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
@RunWith ( CiRunner.class )
public class DIRSERVER169IT
{
    public static DirectoryService service;


    /**
     * @todo replace this later with an Ldif tag
     *
     * @throws NamingException on error
     */
    protected void createData() throws NamingException
    {
        LdapContext sysRoot = getSystemContext( service );

        Attributes people = new AttributesImpl( true );
        Attribute attribute = new AttributeImpl( "objectClass" );
        attribute.add( "top" );
        attribute.add( "organizationalUnit" );
        people.put( attribute );
        people.put( "ou", "people" );
        sysRoot.createSubcontext( "ou=people", people );

        Attributes user = new AttributesImpl( "uid", "bob" );
        user.put( "cn", "Bob Hamilton" );
        user.put( "userPassword", "bobspassword" );

        Attribute objectClass = new AttributeImpl( "objectClass" );
        user.put( objectClass );
        objectClass.add( "top" );
        objectClass.add( "person" );
        objectClass.add( "organizationalPerson" );
        objectClass.add( "inetOrgPerson" );
        user.put( "sn", "Hamilton" );

        sysRoot.createSubcontext( "uid=bob,ou=people", user );
    }


    @Test
    public void testSearchResultNameIsRelativeToSearchContext() throws Exception
    {
        // @todo replace with ldif tags
        createData();

        LdapContext sysRoot = getSystemContext( service );

        Hashtable<String,Object> env = new Hashtable<String,Object>();
        env.put( DirectoryService.JNDI_KEY, service );
        env.put( Context.INITIAL_CONTEXT_FACTORY, CoreContextFactory.class.getName() );
        env.put( Context.PROVIDER_URL, "ou=system" );

        DirContext ctx = new InitialDirContext( env );
        SearchControls ctls = new SearchControls();
        String searchBase = "ou=people";

        NamingEnumeration<SearchResult> results = ctx.search( searchBase, "(uid=bob)", ctls );
        assertTrue( results.hasMore() );
        SearchResult searchResult = results.next();

        StringBuffer userDn = new StringBuffer();
        userDn.append( searchResult.getName() );

        // Note that only if it's returned as a relative name do you need to 
        // add the search base to the returned name value 
        if ( searchResult.isRelative() )
        {
            if ( searchBase.length() > 0 )
            {
                userDn.append( "," );
                userDn.append( searchBase );
            }
            userDn.append( "," );
            userDn.append( ctx.getNameInNamespace() );
        }
        
        assertEquals( "uid=bob,ou=people," + sysRoot.getNameInNamespace(), userDn.toString() );
    }


    /**
     * Search over binary attributes now should work via the core JNDI 
     * provider.
     *
     * @throws Exception if there are errors
     */
    @Test
    public void testPasswordComparisonSucceeds() throws Exception
    {
        // @todo replace with ldif tags
        createData();

        Hashtable<String,Object> env = new Hashtable<String,Object>();
        env.put( DirectoryService.JNDI_KEY, service );
        env.put( Context.INITIAL_CONTEXT_FACTORY, CoreContextFactory.class.getName() );
        env.put( Context.PROVIDER_URL, "ou=system" );

        DirContext ctx = new InitialDirContext( env );
        SearchControls ctls = new SearchControls();
        ctls.setReturningAttributes( new String[0] );
        ctls.setSearchScope( SearchControls.OBJECT_SCOPE );

        String filter = "(userPassword={0})";
        NamingEnumeration<SearchResult> results = 
            ctx.search( "uid=bob,ou=people", filter, new Object[] { "bobspassword" }, ctls );

        // We should have a match
        assertTrue( results.hasMore() );
    }
}
