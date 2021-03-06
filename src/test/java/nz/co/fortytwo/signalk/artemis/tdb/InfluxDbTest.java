package nz.co.fortytwo.signalk.artemis.tdb;

import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.UPDATES;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.dot;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.values;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.vessels;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.influxdb.dto.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import io.netty.util.internal.MathUtil;
import mjson.Json;
import nz.co.fortytwo.signalk.artemis.intercept.BaseInterceptor;
import nz.co.fortytwo.signalk.artemis.server.BaseServerTest;
import nz.co.fortytwo.signalk.artemis.tdb.InfluxDbService;
import nz.co.fortytwo.signalk.artemis.util.SignalKConstants;
import nz.co.fortytwo.signalk.artemis.util.SignalkMapConvertor;

public class InfluxDbTest {

	private static Logger logger = LogManager.getLogger(InfluxDbTest.class);
	private InfluxDbService influx;
	//private JsonSerializer ser = new JsonSerializer();
	@Before
	public void setUpInfluxDb() {
		logger.debug("Start influxdb client");
		influx = new InfluxDbService(BaseServerTest.SIGNALK_TEST_DB);
		influx.setWrite(true);
	}

	@After
	public void closeInfluxDb() {
		influx.close();
	}
	
	private void clearDb(){
		influx.getInfluxDB().query(new Query("drop measurement vessels", BaseServerTest.SIGNALK_TEST_DB));
		influx.getInfluxDB().query(new Query("drop measurement sources", BaseServerTest.SIGNALK_TEST_DB));
		//influx.getInfluxDB().query(new Query("drop measurement config", BaseServerTest.SIGNALK_TEST_DB));
		influx.getInfluxDB().query(new Query("drop measurement resources", BaseServerTest.SIGNALK_TEST_DB));
		influx.loadPrimary();
	}
	
	@Test
	public void shouldProcessFullModel() throws IOException {
		
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/docs-data_model.json");
		assertEquals(13,map.size());
	}

	

	@Test
	public void shouldSaveMultipleValuesAndReturnLatest() throws Exception {
		clearDb();
		influx.setPrimary("vessels.urn:mrn:signalk:uuid:c0d79334-4e25-4245-8892-54e8ccc8021d.navigation.courseOverGroundTrue","ttyUSB0.GP.sentences.RMC");
		// get a sample of signalk
		Json json = getJson("./src/test/resources/samples/full/docs-data_model_multiple_values.json");
		NavigableMap<String, Json> map = getJsonMap(json);
		//save and flush
		
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb("urn:mrn:signalk:uuid:c0d79334-4e25-4245-8892-54e8ccc8021d");
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveFullModelAndReturnLatest() throws Exception {
		clearDb();
		// get a sample of signalk
		Json json = getJson("./src/test/resources/samples/full/docs-data_model.json");
		NavigableMap<String, Json> map = getJsonMap(json);
		
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb("urn:mrn:signalk:uuid:705f5f1a-efaf-44aa-9cb8-a0fd6305567c");
		logger.debug("Map: {}", map);
		for(String k:map.keySet()){
			logger.debug("Put Key: {}",k);
			if(!k.contains(SignalKConstants.nav))continue;
			assertTrue(k.contains(dot+values+dot));
		}
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveFullModelAndReturnLatestWithEdit() throws Exception {
		clearDb();
		// get a sample of signalk
		///artemis-server/src/test/resources/samples/full/docs-data_model.json
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/docs-data_model.json");
		
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb("urn:mrn:signalk:uuid:705f5f1a-efaf-44aa-9cb8-a0fd6305567c");
		compareMaps(map,rslt);
		//now run again with variation
		map.put("vessels.urn:mrn:signalk:uuid:705f5f1a-efaf-44aa-9cb8-a0fd6305567c.navigation.headingMagnetic.value",Json.make(6.55));
		
		//save and flush
		influx.save(map);
		//reload
		rslt = influx.loadData(map,"select * from vessels group by skey, primary, uuid,grp order by time desc limit 1");
		//check for .values.
		logger.debug("Map: {}", map);
		
		logger.debug(SignalkMapConvertor.mapToFull(map));
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveFullModelSamples() throws Exception {
		File dir = new File("./src/test/resources/samples/full");
		for(File f:dir.listFiles()){
			if(f.isDirectory())continue;
			clearDb();
			logger.debug("Testing sample:"+f.getName());
			//flush primaryMap
			influx.loadPrimary();
			// get a sample of signalk
			NavigableMap<String, Json> map = getJsonMap(f.getAbsolutePath());
		
			//save and flush
			influx.save(map);
			//reload from db
			NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString(),map.get("version").asString());
			compareMaps(map,rslt);
		}
	}
	
	@Test
	 @Ignore
	public void shouldSaveDeltaSamples() throws Exception {
		File dir = new File("./src/test/resources/samples/delta");
		BaseInterceptor base = new BaseInterceptor();
		for(File f:dir.listFiles()){
			if(f.isDirectory())continue;
			clearDb();
			logger.debug("Testing sample:"+f.getName());
			// get a sample of signalk
			
			Json input = Json.read(FileUtils.readFileToString(f));
			for(String key: input.asJsonMap().keySet()) {
				if(input.at(key).isArray()) {
					input.at(key).asJsonList().forEach((j) -> {
//						base.convertSource(j,"internal", "signalk");
					});
				}
			}
			
			NavigableMap<String, Json> map = getJsonDeltaMap(input);
		
			//save and flush
			influx.save(map);
			//reload from db
			NavigableMap<String, Json> rslt = loadFromDb();
			//compareMaps(map,rslt);
			ArrayList<String> all = new ArrayList<>();
			all.add("all");
			compareMaps(map,rslt);
		}
	}
	
	@Test
	 @Ignore
	public void testFullResources() throws Exception {
		clearDb();
		// get a hash of signalk
		String body = FileUtils.readFileToString(new File("./src/test/resources/samples/full_resources.json"));
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<String, Json>();
		Json input = Json.read(body);
		BaseInterceptor base = new BaseInterceptor();
//		base.convertFullSrcToRef(input, "internal", "signalk");
		SignalkMapConvertor.parseFull(input, map, "");
		
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb();
		compareMaps(map,rslt);
	}
	
	

	@Test
	public void testConfigJson() throws Exception {
		clearDb();
		influx.getInfluxDB().query(new Query("drop measurement config", BaseServerTest.SIGNALK_TEST_DB));
		// get a hash of signalk
		String body = FileUtils.readFileToString(new File("./src/test/resources/samples/signalk-config.json"));
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<String, Json>();
		SignalkMapConvertor.parseFull(Json.read(body), map, "");
		
		influx.save(map);
		
		//reload from db
		NavigableMap<String, Json> rslt = loadConfigFromDb();
	
		compareMaps(map,rslt);
	}
	
	
	

	@Test
	public void testPKTreeJson() throws Exception {
		clearDb();
		// get a hash of signalk
		String body = FileUtils.readFileToString(new File("./src/test/resources/samples/PK_tree.json"));
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<String, Json>();
		SignalkMapConvertor.parseFull(Json.read(body), map, "");
		map.forEach((t, u) -> logger.debug(t + "=" + u));
		map.forEach((k, v) -> influx.save(map));
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb();
		compareMaps(map,rslt);
	}
	
	private void compareMaps(NavigableMap<String, Json> map, NavigableMap<String, Json> rslt) throws Exception {
		
		ArrayList<String> all = new ArrayList<>();
		all.add("all");
		Json mapRslt = SignalkMapConvertor.mapToFull(map);
		Json jsonRslt = SignalkMapConvertor.mapToFull(rslt);
		logger.debug("Json source: {}",mapRslt);
		logger.debug("Json result: {}",jsonRslt);
		//remove uuid
		mapRslt.delAt("self");
		mapRslt.delAt("version");
		jsonRslt.delAt("self");
		jsonRslt.delAt("version");
		//assertEquals(mapRslt,jsonRslt );
		assertTrue(compare(mapRslt,jsonRslt));
		
	}
	
	private boolean compare(Json map, Json rslt) {
		if(map.isPrimitive()|| map.isArray()) {
			logger.debug("Matching {} |  {}", map,rslt);
			if(map.isNumber()) {
				//logger.debug("Matching: {} - {} = {}",map.asDouble(,)rslt.asDouble()
				return Math.abs(map.asDouble()-rslt.asDouble())<0.0000001;
			}
			return map.equals(rslt);
		}
		for(String key:map.asJsonMap().keySet()) {
			//logger.debug("Match {} ", key);
			//logger.debug("Match {} : {}", map.at(key),rslt.at(key));
			if( !map.at(key).equals(rslt.at(key))) {
				logger.debug("Bad match {} is not  {}", map.at(key),rslt.at(key));
				return false;
			}
			return compare(map.at(key),rslt.at(key));
		}
		return true;
	}
	private Json getJson(String file) throws IOException {
		String body = FileUtils.readFileToString(new File(file));
		Json json = Json.read(body);
		return json;
	}
	private NavigableMap<String, Json> getJsonMap(Json json) throws IOException {
		//convert to map
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<String, Json>();
		SignalkMapConvertor.parseFull(json, map, "");
		map.forEach((t, u) -> logger.debug(t + "=" + u));
		return map;
	}
	private NavigableMap<String, Json> getJsonMap(String file) throws IOException {
		//convert to map
		Json json = getJson(file);
		return getJsonMap(json);
	}
	
	private NavigableMap<String, Json> getJsonDeltaMap(Json json) throws Exception {
		
		//convert to map
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<String, Json>();
		SignalkMapConvertor.parseDelta(json, map);
		map.forEach((t, u) -> logger.debug(t + "=" + u));
		return map;
	}
	
	private NavigableMap<String, Json> loadFromDb(String self) {
		return loadFromDb(self,"1.0.0");
	}
	private NavigableMap<String, Json> loadFromDb(String self, String version) {
		NavigableMap<String, Json> rslt = loadFromDb();
		
		//add std entries
		rslt.put("self",Json.make(self));
		rslt.put("version",Json.make(version));
		
		logger.debug("self",Json.make(self));
		logger.debug("version",Json.make(version));
		return rslt;
	}
	
	private NavigableMap<String, Json> loadFromDb() {
		
		NavigableMap<String, Json> rslt = new ConcurrentSkipListMap<String, Json>();
		rslt = influx.loadData(rslt, vessels, new HashMap<String, String>());
		rslt = influx.loadSources(rslt,new HashMap<String, String>());
		rslt = influx.loadResources(rslt,new HashMap<String, String>());
		rslt.forEach((t, u) -> logger.debug(t + "=" + u));
		return rslt;
	}
	
	private NavigableMap<String, Json> loadConfigFromDb() {
		NavigableMap<String, Json> rslt = new ConcurrentSkipListMap<String, Json>();
		
		rslt = influx.loadConfig(rslt,"select * from config group by skey,uuid order by time desc limit 1");
		
		rslt.forEach((t, u) -> logger.debug(t + "=" + u));
		return rslt;
	}
}
