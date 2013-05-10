
package com.badlogic.gdx.maps.tiled;

import java.io.IOException;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.ImageResolver;
import com.badlogic.gdx.maps.ImageResolver.AssetManagerImageResolver;
import com.badlogic.gdx.maps.ImageResolver.DirectImageResolver;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader.Element;

public class TideMapLoader extends BaseTideMapLoader<TiledMap, TideMapLoader.Parameters> {

	public static class Parameters extends AssetLoaderParameters<TiledMap> {
		/** Whether to load the map for a y-up coordinate system */
		public boolean yUp = true;
		/** generate mipmaps? **/
		public boolean generateMipMaps = false;
		/** The TextureFilter to use for minification **/
		public TextureFilter textureMinFilter = TextureFilter.Nearest;
		/** The TextureFilter to use for magnification **/
		public TextureFilter textureMagFilter = TextureFilter.Nearest;
	}

	private final ObjectMap<String, Texture> textures = new ObjectMap<String, Texture>();
	private AssetManager assetManager;
	private ImageResolver resolver;
	private Parameters parameters;

	public TideMapLoader () {
		super(new InternalFileHandleResolver());
	}

	public TideMapLoader (FileHandleResolver resolver) {
		super(resolver);
	}

	public TiledMap load (String fileName) {
		return load(fileName, createParameters());
	}

	@Override
	public void parseParameters (Parameters parameters, AssetManager assetManager) {
		this.assetManager = assetManager;
		this.parameters = parameters;
	}

	@Override
	public TiledMap createTiledMap () {
		return new TiledMap();
	}

	@Override
	public Parameters createParameters () {
		return new Parameters();
	}

	@Override
	public boolean isYUp () {
		return parameters.yUp;
	}

	@Override
	public ObjectMap<String, ? extends Disposable> requestResources (FileHandle mapFile, Element root, Parameters parameters) {
		try {
			for (FileHandle textureFile : loadTileSheets(root, mapFile)) {
				Texture texture = new Texture(textureFile, parameters.generateMipMaps);
				texture.setFilter(parameters.textureMinFilter, parameters.textureMagFilter);
				textures.put(textureFile.path(), texture);
			}
		} catch (IOException e) {
			throw new GdxRuntimeException("Couldn't load tilemap '" + mapFile.path() + "'", e);
		}

		return textures;
	}

	@Override
	public Array<AssetDescriptor> requestDependancies (FileHandle mapFile, Element root, Parameters parameters) {
		Array<AssetDescriptor> dependencies = new Array<AssetDescriptor>();

		boolean generateMipMaps = (parameters != null ? parameters.generateMipMaps : false);
		TextureLoader.TextureParameter texParams = new TextureParameter();
		texParams.genMipMaps = generateMipMaps;
		if (parameters != null) {
			texParams.minFilter = parameters.textureMinFilter;
			texParams.magFilter = parameters.textureMagFilter;
		}

		try {
			for (FileHandle image : loadTileSheets(root, mapFile)) {
				dependencies.add(new AssetDescriptor(image.path(), Texture.class, texParams));
			}
		} catch (IOException e) {
			throw new GdxRuntimeException("Couldn't load tilemap '" + mapFile.path() + "'", e);
		}

		return dependencies;
	}

	@Override
	public void populateWithTiles (TiledMapTileSet tileset, TiledMap map, FileHandle mapFile, FileHandle tilesetImage) {
		int tilewidth = tileset.getProperties().get("tilewidth", Integer.class);
		int tileheight = tileset.getProperties().get("tileheight", Integer.class);
		int spacingX = tileset.getProperties().get("spacingX", Integer.class);
		int spacingY = tileset.getProperties().get("spacingY", Integer.class);
		int marginX = tileset.getProperties().get("marginX", Integer.class);
		int marginY = tileset.getProperties().get("marginY", Integer.class);
		int firstgid = tileset.getProperties().get("firstgid", Integer.class);

		if (assetManager == null) {
			// sync load
			resolver = new DirectImageResolver(textures);
		} else {
			// async load
			resolver = new AssetManagerImageResolver(assetManager);
		}

		TextureRegion texture = resolver.getImage(tilesetImage.path());
		int stopWidth = texture.getRegionWidth() - tilewidth;
		int stopHeight = texture.getRegionHeight() - tileheight;

		int id = firstgid;

		for (int y = marginY; y <= stopHeight; y += tileheight + spacingY) {
			for (int x = marginX; x <= stopWidth; x += tilewidth + spacingX) {
				TextureRegion tileRegion = new TextureRegion(texture, x, y, tilewidth, tileheight);
				if (!parameters.yUp) {
					tileRegion.flip(false, true);
				}
				TiledMapTile tile = new StaticTiledMapTile(tileRegion);
				tile.setId(id);
				tileset.putTile(id++, tile);
			}
		}

	}

	/** Loads the tilesets
	 * @param root the root XML element
	 * @return a list of filenames for images containing tiles
	 * @throws IOException */
	private Array<FileHandle> loadTileSheets (Element root, FileHandle mapFile) throws IOException {
		Array<FileHandle> images = new Array<FileHandle>();
		Element tilesheets = root.getChildByName("TileSheets");
		for (Element tileset : tilesheets.getChildrenByName("TileSheet")) {
			Element imageSource = tileset.getChildByName("ImageSource");
			FileHandle image = getRelativeFileHandle(mapFile, imageSource.getText());
			images.add(image);
		}
		return images;
	}
}
