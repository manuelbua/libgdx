
package com.badlogic.gdx.maps.tiled;

import java.io.IOException;
import java.util.StringTokenizer;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlReader.Element;

public abstract class BaseTiledMapLoader<T extends TiledMap, P extends AssetLoaderParameters<T>> extends
	AsynchronousAssetLoader<T, P> {

	protected static final int FLAG_FLIP_HORIZONTALLY = 0x80000000;
	protected static final int FLAG_FLIP_VERTICALLY = 0x40000000;
	protected static final int FLAG_FLIP_DIAGONALLY = 0x20000000;
	protected static final int MASK_CLEAR = 0xE0000000;

	// xml parsing
	protected XmlReader xml = new XmlReader();
	protected Element root;

	// map data
	protected int mapWidthInPixels;
	protected int mapHeightInPixels;
	protected T map;

	public BaseTiledMapLoader (FileHandleResolver resolver) {
		super(resolver);
	}

	public T load (String fileName, P parameters) {
		try {
			parseParameters((parameters == null ? createParameters() : parameters), null);

			FileHandle mapFile = resolve(fileName);
			root = xml.parse(mapFile);
			ObjectMap<String, ? extends Disposable> data = requestResources(mapFile, root, parameters);
			T map = loadTilemap(root, mapFile);
			map.setOwnedResources(data.values().toArray());

			finishLoading(parameters);

			return map;
		} catch (IOException e) {
			throw new GdxRuntimeException("Couldn't load tilemap '" + fileName + "'", e);
		}
	}

	@Override
	public void loadAsync (AssetManager manager, String fileName, P parameter) {
		map = null;

		FileHandle mapFile = resolve(fileName);
		parseParameters((parameter == null ? createParameters() : parameter), manager);

		try {
			map = loadTilemap(root, mapFile);
		} catch (Exception e) {
			throw new GdxRuntimeException("Couldn't load tilemap '" + fileName + "'", e);
		}
	}

	@Override
	public T loadSync (AssetManager manager, String fileName, P parameter) {
		finishLoading(parameter);
		return map;
	}

	@Override
	public Array<AssetDescriptor> getDependencies (String fileName, P parameter) {
		Array<AssetDescriptor> dependencies = new Array<AssetDescriptor>();
		try {
			FileHandle tmxFile = resolve(fileName);
			root = xml.parse(tmxFile);
			return requestDependancies(tmxFile, root, parameter);
		} catch (IOException e) {
			throw new GdxRuntimeException("Couldn't load tilemap '" + fileName + "'", e);
		}
	}

	// shared utilities

	protected void loadProperties (MapProperties properties, Element element) {
		if (element.getName().equals("properties")) {
			for (Element property : element.getChildrenByName("property")) {
				String name = property.getAttribute("name", null);
				String value = property.getAttribute("value", null);
				if (value == null) {
					value = property.getText();
				}
				properties.put(name, value);
			}
		}
	}

	protected static FileHandle getRelativeFileHandle (FileHandle file, String path) {
		StringTokenizer tokenizer = new StringTokenizer(path, "\\/");
		FileHandle result = file.parent();
		while (tokenizer.hasMoreElements()) {
			String token = tokenizer.nextToken();
			if (token.equals(".."))
				result = result.parent();
			else {
				result = result.child(token);
			}
		}
		return result;
	}

	protected static int unsignedByteToInt (byte b) {
		return (int)b & 0xFF;
	}

	/** @param assetManager can be null, means the loading is synchronous */
	public abstract void parseParameters (P parameters, AssetManager assetManager);

	public abstract Array<AssetDescriptor> requestDependancies (FileHandle tmxFile, Element root, P parameters);

	public abstract ObjectMap<String, ? extends Disposable> requestResources (FileHandle mapFile, Element root, P parameters);

	public abstract void finishLoading (P parameters);

	public abstract T loadTilemap (Element root, FileHandle mapFile);

	public abstract P createParameters ();
}
