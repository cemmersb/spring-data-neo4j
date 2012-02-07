/**
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.neo4j.repository;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.graphdb.Node;
import org.neo4j.helpers.collection.IteratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.neo4j.model.*;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.data.neo4j.support.conversion.NoSuchColumnFoundException;
import org.springframework.data.neo4j.support.node.Neo4jHelper;
import org.springframework.test.context.CleanContextCacheTestExecutionListener;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.junit.internal.matchers.IsCollectionContaining.hasItem;
import static org.junit.internal.matchers.IsCollectionContaining.hasItems;
import static org.neo4j.helpers.collection.IteratorUtil.addToCollection;
import static org.neo4j.helpers.collection.IteratorUtil.asCollection;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@TestExecutionListeners({CleanContextCacheTestExecutionListener.class, DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class})
public class GraphRepositoryTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private Neo4jTemplate neo4jTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private BeingRepository beingRepository;
    @Autowired
    GroupRepository groupRepository;

    @Autowired
    FriendshipRepository friendshipRepository;

    private TestTeam testTeam;

    @BeforeTransaction
    public void cleanDb() {
        Neo4jHelper.cleanDb(neo4jTemplate);
    }

    @Before
    public void setUp() throws Exception {
        testTeam = new TestTeam();
        testTeam.createSDGTeam( personRepository, groupRepository, friendshipRepository );
    }

    @Test
    public void deleteAll() {
        assertThat(personRepository.count(), is(3L));
        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                personRepository.deleteAll();
            }
        });
        assertThat(personRepository.count(), is(0L));
    }

    @Test
    public void deleteCollection() {
        assertThat(personRepository.count(), is(3L));
        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                personRepository.delete(asList(testTeam.michael,testTeam.david));
            }
        });
        assertThat(personRepository.count(), is(1L));
    }

    @Test
    public void deleteById() {
        final Long id = testTeam.michael.getId();
        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                personRepository.delete(id);
            }
        });
        assertThat(personRepository.exists(id), is(false));
    }
    @Test
    public void deleteSingle() {
        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                personRepository.delete(testTeam.michael);
            }
        });
        assertThat(personRepository.exists(testTeam.michael.getId()), is(false));
    }

    @Test @Transactional 
    public void testFindIterableOfPersonWithQueryAnnotation() {
        Iterable<Person> teamMembers = personRepository.findAllTeamMembers(testTeam.sdg);
        assertThat( asCollection( teamMembers ), hasItems( testTeam.michael, testTeam.david, testTeam.emil ) );
    }

    @Test @Transactional
    public void testFindIterableOfPersonWithQueryAnnotationSpatial() {
        Iterable<Person> teamMembers = personRepository.findWithinBoundingBox("personLayer", 55, 15, 57, 17);
        assertThat(asCollection(teamMembers), hasItems(testTeam.michael, testTeam.david));
    }

    @Test @Transactional
    public void testFindIterableOfPersonWithQueryAnnotationAndGremlin() {
        Iterable<Person> teamMembers = personRepository.findAllTeamMembersGremlin( testTeam.sdg );
        assertThat( asCollection( teamMembers ), hasItems( testTeam.michael, testTeam.david, testTeam.emil ) );
    }

    @Test @Transactional 
    public void testFindPersonWithQueryAnnotation() {
        Person boss = personRepository.findBoss( testTeam.michael );
        assertThat(boss, is( testTeam.emil ));
    }

    @Test @Transactional 
    public void testFindPersonWithQueryAnnotationUsingLongAsParameter() {
        Person boss = personRepository.findBoss( testTeam.michael.getId() );
        assertThat(boss, is( testTeam.emil ));
    }

    @Test @Transactional 
    public void shouldBeAbleToTurnQueryResultsToAMapResultInterface() throws Exception {
        MemberData first = personRepository.findMemberData(testTeam.michael).iterator().next();

        assertThat(first.getBoss(), is(testTeam.emil));
        assertThat(asCollection(first.getTeams()), hasItem(testTeam.sdg));
    }

    @Test @Transactional 
    public void testFindIterableMapsWithQueryAnnotation() {
        Iterable<Map<String, Object>> teamMembers = personRepository.findAllTeamMemberData(testTeam.sdg);
        assertThat(asCollection(teamMembers), hasItems(testTeam.simpleRowFor(testTeam.michael, "member"), testTeam.simpleRowFor(testTeam.david, "member"), testTeam.simpleRowFor(testTeam.emil, "member")));
    }

    @Test @Transactional 
    @Ignore("untyil cypher supports parameters in path's and skip, limit")
    public void testFindWithMultipleParameters() {
        final int depth = 1;
        final int limit = 2;
        Iterable<Person> teamMembers = personRepository.findSomeTeamMembers(testTeam.sdg.getName(), 0, limit, depth);
        assertThat(asCollection(teamMembers), hasItems(testTeam.michael, testTeam.david));
    }

    @Test @Transactional 
    public void testFindPaged() {
        final PageRequest page = new PageRequest(0, 1, Sort.Direction.ASC, "member.name","member.age");
        Page<Person> teamMemberPage1 = personRepository.findAllTeamMembersPaged(testTeam.sdg, page);
        assertThat(teamMemberPage1, hasItem(testTeam.david));
    }

    @Test @Transactional 
    public void testFindPagedDescending() {
        final PageRequest page = new PageRequest(0, 2, Sort.Direction.DESC, "member.name");
        Page<Person> teamMemberPage1 = personRepository.findAllTeamMembersPaged(testTeam.sdg, page);
        assertEquals(asList(testTeam.michael, testTeam.emil), asCollection(teamMemberPage1));
        assertThat(teamMemberPage1.isFirstPage(), is(true));
    }

    @Test @Transactional 
    public void testFindPagedNull() {
        Page<Person> teamMemberPage1 = personRepository.findAllTeamMembersPaged(testTeam.sdg, null);
        assertEquals(new HashSet(asList(testTeam.david, testTeam.emil, testTeam.michael)), addToCollection(teamMemberPage1, new HashSet()));
        assertThat(teamMemberPage1.isFirstPage(), is(true));
        assertThat(teamMemberPage1.isLastPage(), is(false));
    }

    @Test @Transactional 
    public void testFindSortedDescending() {
        final Sort sort = new Sort(Sort.Direction.DESC, "member.name");
        Iterable<Person> teamMembers = personRepository.findAllTeamMembersSorted(testTeam.sdg, sort);
        assertEquals(asList(testTeam.michael, testTeam.emil, testTeam.david), asCollection(teamMembers));
    }

    @Test
    @Transactional
    public void testGetStoredJavaType() {
        Person p = personRepository.save(new Person());
        assertEquals(Person.class, personRepository.getStoredJavaType(p));
        assertEquals(Person.class, personRepository.getStoredJavaType(neo4jTemplate.getPersistentState(p)));
        assertEquals(null, personRepository.getStoredJavaType(new Person()));
    }
    @Test
    @Transactional
    public void testTemplateGetStoredJavaType() {
        Person p = neo4jTemplate.save(new Person());
        assertEquals(Person.class, neo4jTemplate.getStoredJavaType(p));
        assertEquals(Person.class, neo4jTemplate.getStoredJavaType(neo4jTemplate.getPersistentState(p)));
        assertEquals(null, neo4jTemplate.getStoredJavaType(new Person()));
    }


    @Test @Transactional 
    public void testFindSortedNull() {
        Collection<Person> teamMembers = IteratorUtil.asCollection(personRepository.findAllTeamMembersSorted(testTeam.sdg, null));
        assertThat(teamMembers, hasItems(testTeam.michael, testTeam.emil, testTeam.david));
    }

    @Test @Transactional 
    public void testFindByNamedQuery() {
        Group team = personRepository.findTeam(testTeam.michael);
        assertThat(team, is(testTeam.sdg));
    }

    @Test @Transactional 
    public void findByName() {
        Iterable<Person> findByName = personRepository.findByName(testTeam.michael.getName());
        assertThat(findByName, hasItem(testTeam.michael));
    }

    @Test @Transactional
    public void findByPersonalityEnum() {
        testTeam.michael.setPersonality(Personality.EXTROVERT);
        neo4jTemplate.save(testTeam.michael);
        Iterable<Person> result = personRepository.findByPersonality(Personality.EXTROVERT.name());
        assertThat(result, hasItem(testTeam.michael));
    }
    @Test( expected = NoSuchColumnFoundException.class) @Transactional
    public void missingColumnIsReportedNicely() {
        Iterable<MemberData> findByName = personRepository.nonWorkingQuery( testTeam.michael );
        Person boss = findByName.iterator().next().getBoss();
    }

    @Test @Transactional
    public void findByFullTextName() {
        testTeam.sdg.setFullTextName("test");
        neo4jTemplate.save(testTeam.sdg);
        final Iterable<Group> groups = groupRepository.findByFullTextNameLike("te*");
        assertThat(groups, hasItem(testTeam.sdg));
    }
    @Test @Transactional 
    public void findPageByName() {
        final Iterable<Group> groups = groupRepository.findByName(testTeam.sdg.getName(), new PageRequest(0, 1));
        assertThat(groups, hasItem(testTeam.sdg));
    }

    @Test @Transactional
    public void testCustomImplementation() {
        final Friendship friendship = personRepository.befriend(testTeam.michael, testTeam.emil);
        assertNotNull(friendship.getId());
        final Person loaded = personRepository.findOne(testTeam.michael.getId());
        assertEquals(2, IteratorUtil.count(loaded.getFriendships()));
        assertThat(loaded.getFriendships(),hasItem(friendship));
    }

    @Test @Transactional
    public void testCreateDuplicateRelationship() {
        assertEquals(1, IteratorUtil.count(neo4jTemplate.<Node>getPersistentState(testTeam.michael).getRelationships(Person.KNOWS)));
        personRepository.createDuplicateRelationshipBetween(testTeam.michael, testTeam.david, Friendship.class, "knows");
        assertEquals(2, IteratorUtil.count(neo4jTemplate.<Node>getPersistentState(testTeam.michael).getRelationships(Person.KNOWS)));
    }

    @Test @Transactional
    public void testUseInterfaceInRelationships() {
        final Person p = neo4jTemplate.save(new Person());
        final Group group = neo4jTemplate.save(new Group());
        neo4jTemplate.createRelationshipBetween(neo4jTemplate.<Node>getPersistentState(p),neo4jTemplate.<Node>getPersistentState(group),"interface_test",null);
        final Person p2 = neo4jTemplate.fetch(p);
        assertEquals(group,IteratorUtil.firstOrNull(p2.getGroups()));
    }

    @Test @Transactional
    public void testConnectToRootEntity() {
        final Node referenceNode = neo4jTemplate.getReferenceNode();
        neo4jTemplate.postEntityCreation(referenceNode,RootEntity.class);
        final RootEntity root = neo4jTemplate.findOne(referenceNode.getId(), RootEntity.class);
        root.setRootName("RootName");
        neo4jTemplate.save(root);
        assertEquals(referenceNode.getId(), (long) root.getId());
        assertEquals("RootName", referenceNode.getProperty("rootName"));
        assertEquals("RootName", root.getRootName());

        final Person person = new Person();
        person.setRoot(root);
        neo4jTemplate.save(person);
        final Person p2 = neo4jTemplate.findOne(person.getId(), Person.class);
        assertEquals(root.getId(),p2.getRoot().getId());
    }

    @Test
    public void testUseInterfaceAsPersistentEntity() {
        final List<Being> beings = beingRepository.findAll().as(List.class);
        assertEquals(3,beings.size());
    }
}
