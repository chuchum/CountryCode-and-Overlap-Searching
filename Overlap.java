package org.geotools.tutorial;

import java.io.File;
import java.io.FileReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.opencsv.CSVReader;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.IntersectionMatrix;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.operation.overlay.OverlayOp;

public class Overlap {

	static ArrayList<Geometry> wktList = new ArrayList<Geometry>();
	static ArrayList<String> idList = new ArrayList<String>();
	static ArrayList<String> addList = new ArrayList<String>();
	static int i;
	static int j;
	// static String input;

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub

		// 若要採用Windows外觀 則String為
		UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

		File file = JFileDataStoreChooser.showOpenFile("csv", null);
		if (file == null) {
			return;
		}

		final SimpleFeatureType TYPE = DataUtilities.createType("Location",
				"the_geom:MultiPolygon:srid=4326," +"ID:String," + "P Code:String," + "Usage Band:Integer," + "Wkt:String");
		System.out.println("TYPE:" + TYPE);

		/*
		 * A list to collect features as we create them.
		 */
		List<SimpleFeature> features = new ArrayList<>();

		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

		CSVReader reader = new CSVReader(new FileReader(file));

		System.out.println("-----------------------------");

		char colon = ':';
		String[] r1;
		while ((r1 = reader.readNext()) != null) {
			if (r1[0].charAt(0) != colon) {
				String Pcode = "" + r1[0].charAt(0) + r1[0].charAt(1);
				// System.out.println("Pcode: " + Pcode);
				int UsageBand = r1[0].charAt(2) - '0';
				// System.out.println("Usage Band: " + UsageBand);

				/* 將經緯座標範圍轉換成WKT */
				String wkttt = "POLYGON ((" + r1[7] + " " + r1[6] + "," + r1[7] + " " + r1[8] + "," + r1[9] + " "
						+ r1[8] + "," + r1[9] + " " + r1[6] + "," + r1[7] + " " + r1[6] + "))";
				String id = r1[0];
				WKTReader wktreader = new WKTReader();
				Geometry wkt = wktreader.read(wkttt);
				// System.out.println("WKT: " + wkt);
				featureBuilder.add(wkt);
				featureBuilder.add(r1[0]);
				featureBuilder.add(Pcode);
				featureBuilder.add(UsageBand);
				featureBuilder.add(wkt);
				SimpleFeature feature = featureBuilder.buildFeature(null);
				features.add(feature);
				wktList.add(wkt);
				idList.add(id);

			}

		}

		// NO2A2852.000
		Scanner scanner = new Scanner(System.in);
		String input = JOptionPane.showInputDialog(null, "請輸入ID:", "輸入對話框", JOptionPane.QUESTION_MESSAGE);
		// String input = scanner.nextLine();
		/* 1.偵測輸入 2.去除沒有重疊 3.去除只有touch的wkt */
		for (i = 0; i < wktList.size(); i++) {
			for (j = 0; j < wktList.size(); j++) {
				if (input.equals(idList.get(i))) {
					if (OverlayOp.overlayOp(wktList.get(i), wktList.get(j), 1).isEmpty() == false) {
						if (wktList.get(i).touches(wktList.get(j)) == false) {
							addList.add(idList.get(j));
							//System.out.println(OverlayOp.overlayOp(wktList.get(i), wktList.get(j), 1));
						}
					}
				}
			}
		}
		System.out.println("與  " + input + " Overlap的圖有: " + addList + "\n");
		scanner.close();
		reader.close();

		/*
		 * Get an output file name and create the new shapefile
		 */
		File newFile = getNewShapeFile(file);

		ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

		Map<String, Serializable> params = new HashMap<>();
		params.put("url", newFile.toURI().toURL());
		params.put("create spatial index", Boolean.TRUE);

		ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);

		/*
		 * TYPE is used as a template to describe the file contents
		 */
		newDataStore.createSchema(TYPE);

		/*
		 * Write the features to the shapefile
		 */
		Transaction transaction = new DefaultTransaction("create");

		String typeName = newDataStore.getTypeNames()[0];
		SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
		SimpleFeatureType SHAPE_TYPE = featureSource.getSchema();
		/*
		 * The Shapefile format has a couple limitations: - "the_geom" is always
		 * first, and used for the geometry attribute name - "the_geom" must be
		 * of type Point, MultiPoint, MuiltiLineString, MultiPolygon - Attribute
		 * names are limited in length - Not all data types are supported
		 * (example Timestamp represented as Date)
		 * 
		 * Each data store has different limitations so check the resulting
		 * SimpleFeatureType.
		 */
		System.out.println("SHAPE:" + SHAPE_TYPE);

		if (featureSource instanceof SimpleFeatureStore) {
			SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
			/*
			 * SimpleFeatureStore has a method to add features from a
			 * SimpleFeatureCollection object, so we use the
			 * ListFeatureCollection class to wrap our list of features.
			 */
			SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
			featureStore.setTransaction(transaction);
			try {
				featureStore.addFeatures(collection);
				transaction.commit();
			} catch (Exception problem) {
				problem.printStackTrace();
				transaction.rollback();
			} finally {
				transaction.close();
			}
			System.exit(0); // success!
		} else {
			System.out.println(typeName + " does not support read/write access");
			System.exit(1);
		}
	}

	/**
	 * Prompt the user for the name and path to use for the output shapefile
	 * 
	 * @param csvFile
	 *            the input csv file used to create a default shapefile name
	 * 
	 * @return name and path for the shapefile as a new File object
	 */
	private static File getNewShapeFile(File csvFile) {
		String path = csvFile.getAbsolutePath();
		String newPath = path.substring(0, path.length() - 4) + ".shp";

		JFileDataStoreChooser chooser = new JFileDataStoreChooser("shp");
		chooser.setDialogTitle("Save shapefile");
		chooser.setSelectedFile(new File(newPath));

		int returnVal = chooser.showSaveDialog(null);

		if (returnVal != JFileDataStoreChooser.APPROVE_OPTION) {
			// the user cancelled the dialog
			System.exit(0);
		}

		File newFile = chooser.getSelectedFile();
		if (newFile.equals(csvFile)) {
			System.out.println("Error: cannot replace " + csvFile);
			System.exit(0);
		}

		return newFile;
	}

	/*
	 * public String wkt() { return wkttt; }
	 */

}
