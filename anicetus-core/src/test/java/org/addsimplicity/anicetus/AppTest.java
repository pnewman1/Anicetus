package org.addsimplicity.anicetus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.addsimplicity.anicetus.entity.CompletionStatus;
import org.addsimplicity.anicetus.entity.ExecInfo;
import org.addsimplicity.anicetus.entity.ExecInfoFields;
import org.addsimplicity.anicetus.entity.GlobalInfo;
import org.addsimplicity.anicetus.entity.GlobalInfoFields;
import org.addsimplicity.anicetus.entity.JsonConstants;
import org.addsimplicity.anicetus.entity.TelemetryTransaction;
import org.addsimplicity.anicetus.entity.TransactionFields;
import org.codehaus.jackson.map.JsonNode;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Unit test for simple App.
 */
public class AppTest {
	TelemetryContext m_mgr;
	InMemoryAdapter m_adapter;
	ApplicationContext m_context;

	@Before
	public void init() {
		m_context = new ClassPathXmlApplicationContext(
				new String[] { "org/addsimplicity/anicetus/InMemoryTestBinding.xml" });

		m_mgr = (TelemetryContext) m_context.getBean("manager");

		m_adapter = (InMemoryAdapter) m_context.getBean("stream");
	}

	@Test
	public void testMultiSession() throws Exception {
		m_adapter.clear();

		ExecInfo s = m_mgr.getSession();
		s.setStatus(CompletionStatus.PartialSuccess);
		m_mgr.endSession();

		TelemetryContext mgr = (TelemetryContext) m_context.getBean("manager");
		s = mgr.getSession();
		s.setStatus(CompletionStatus.Failure);
		mgr.endSession();

		List<JsonNode> nodes = m_adapter.getAllObjects();
		assertEquals("Session Count", 2, nodes.size());

		assertEquals("Session 1 Status", CompletionStatus.PartialSuccess.toString(), nodes.get(0).getFieldValue(
				ExecInfoFields.Status.getJsonKey()).getTextValue());
		assertEquals("Session 2 Status", CompletionStatus.Failure.toString(), nodes.get(1).getFieldValue(
				ExecInfoFields.Status.getJsonKey()).getTextValue());
	}

	/**
	 * Simple Test.
	 */
	@Test
	public void testSimpleSession() throws Exception {
		m_adapter.clear();

		ExecInfo s = m_mgr.getSession();
		try {
			Thread.sleep(5);
		}
		catch (InterruptedException ie) {
			ie.printStackTrace();
		}
		s.setStatus(CompletionStatus.Success);
		m_mgr.endSession();

		JsonNode node = m_adapter.getObjectGraph();

		assertEquals("CompletionStatus", CompletionStatus.Success.toString(), node.getFieldValue(
				ExecInfoFields.Status.getJsonKey()).getTextValue());
		int dur = Integer.parseInt(node.getFieldValue(ExecInfoFields.Duration.getJsonKey()).getTextValue());
		assertTrue("Duration", dur >= 5000000);
		assertEquals("OperationName", m_mgr.getOperationName(), node.getFieldValue(
				ExecInfoFields.OperationName.getJsonKey()).getTextValue());
		assertEquals("ReportingNode", m_mgr.getReportingNode(), node.getFieldValue(
				GlobalInfoFields.ReportingNode.getJsonKey()).getTextValue());
		assertNotNull("EntityId", node.getFieldValue(GlobalInfoFields.EntityId.getJsonKey()));
		assertNotNull("TimeStamp", node.getFieldValue(GlobalInfoFields.TimeStamp.getJsonKey()));
	}

	@Test
	public void testStateNEvent() throws Exception {
		m_adapter.clear();

		ExecInfo s = m_mgr.getSession();

		m_mgr.newEvent("testType");
		m_mgr.newState();

		s.setStatus(CompletionStatus.Success);
		m_mgr.endSession();

		JsonNode node = m_adapter.getObjectGraph();

		JsonNode childs = node.getFieldValue("child");
		assertNotNull("Children", childs);
		assertTrue("Array", childs.isArray());
		assertEquals("Length", 2, childs.size());

		String sessid = node.getFieldValue(GlobalInfoFields.EntityId.getJsonKey()).getTextValue();
		assertNotNull("Session", sessid);

		JsonNode ev = childs.getElementValue(0);
		assertEquals("Event Type", "EV", ev.getFieldValue(JsonConstants.EntityType).getTextValue());

		JsonNode stn = childs.getElementValue(1);
		assertEquals("State Type", "ST", stn.getFieldValue(JsonConstants.EntityType).getTextValue());
	}

	@Test
	public void testTransaction() throws Exception {
		m_adapter.clear();

		GlobalInfo s = m_mgr.getSession();
		s.put("x", "y");

		TelemetryTransaction t = m_mgr.beginTransaction("test:url");
		t.setOperationName("xyzz");
		String params[] = { "a", "b" };
		t.setParameters(params);

		m_mgr.endSession();

		JsonNode node = m_adapter.getObjectGraph();

		JsonNode childs = node.getFieldValue("child");
		assertNotNull("Children", childs);
		assertTrue("Array", childs.isArray());
		assertEquals("Length", 1, childs.size());

		JsonNode trans = childs.getElementValue(0);
		assertEquals("Trans Type", "TR", trans.getFieldValue(JsonConstants.EntityType).getTextValue());
		assertEquals("Operation", "xyzz", trans.getFieldValue(ExecInfoFields.OperationName.getJsonKey()).getTextValue());
		assertEquals("Resource", "test:url", trans.getFieldValue(TransactionFields.ResourceId.getJsonKey()).getTextValue());

		JsonNode pnode = trans.getFieldValue(TransactionFields.Parameters.getJsonKey());
		assertNotNull("Params", pnode);
		assertTrue("Param Array", pnode.isArray());
		assertEquals("Param Length", 2, pnode.size());

		assertEquals("Param a", "a", pnode.getElementValue(0).getTextValue());
		assertEquals("Param b", "b", pnode.getElementValue(1).getTextValue());

	}
}
