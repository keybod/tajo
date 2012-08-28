package tajo.engine;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tajo.conf.TajoConf;
import tajo.engine.utils.JVMClusterUtil;

import java.io.IOException;
import java.util.List;

public class MiniTajoCluster {
	static final Log LOG = LogFactory.getLog(MiniTajoCluster.class);
	private TajoConf conf;
	public LocalTajoCluster engineCluster;
	
	public MiniTajoCluster(TajoConf conf, int numLeafServers) throws Exception {
		this.conf = conf;		
		init(numLeafServers);
	}
	
	private void init(int numLeafServers) throws Exception {
		try {
		engineCluster = new LocalTajoCluster(conf,numLeafServers);
		
		engineCluster.startup();
		} catch (IOException e) {
			shutdown();
			throw e;
		}
	}
	
	public JVMClusterUtil.LeafServerThread startLeafServer() throws IOException {
		final TajoConf newConf = new TajoConf(conf);
		
		JVMClusterUtil.LeafServerThread t = null;
		
		t = engineCluster.addLeafServer(newConf, engineCluster.getLeafServers().size());
		t.start();
		t.waitForServerOnline();
		
		return t;
	}
	
	public String abortLeafServer(int index) {
		LeafServer server = getLeafServer(index);
		LOG.info("Aborting " + server.toString());
		server.abort("Aborting for tests", new Exception("Trace info"));
		return server.toString();
	}
	
	public JVMClusterUtil.LeafServerThread stopLeafServer(int index) {
		return stopLeafServer(index);
	}
	
	public JVMClusterUtil.LeafServerThread stopLeafServer(int index, final boolean shutdownFS) {
		JVMClusterUtil.LeafServerThread server = engineCluster.getLeafServers().get(index);
		LOG.info("Stopping " +  server.toString());
		server.getLeafServer().shutdown("Stopping ls " + index);
		return server;
	}
	
	public JVMClusterUtil.MasterThread startMaster() throws Exception {
		TajoConf c = new TajoConf(conf);
		
		JVMClusterUtil.MasterThread t = null;
		
		
		t = engineCluster.addMaster(c, 0);
		t.start();
		t.waitForServerOnline();
		
		return t;		
	}
	
	public TajoMaster getMaster() {
		return this.engineCluster.getMaster();
	}
	
	public void join() {
		this.engineCluster.join();
	}
	
	public void shutdown() {
		if(this.engineCluster != null) {
			this.engineCluster.shutdown();
		}
	}
	
	public List<JVMClusterUtil.LeafServerThread> getLeafServerThreads() {
		return this.engineCluster.getLeafServers();
	}
	
	public List<JVMClusterUtil.LeafServerThread> getLiveLeafServerThreads() {
		return this.engineCluster.getLiveLeafServers();
	}
	
	public LeafServer getLeafServer(int index) {
		return engineCluster.getLeafServer(index);
	}
	
	public JVMClusterUtil.LeafServerThread addLeafServer() throws IOException {
	  return engineCluster.addLeafServer(conf, engineCluster.getClusterSize());
	}
	
	public void shutdownLeafServer(int idx) {
	  engineCluster.getLeafServer(idx).shutdown("Shutting down Normally");
	}
}