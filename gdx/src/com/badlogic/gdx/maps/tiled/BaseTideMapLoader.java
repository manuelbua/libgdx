
package com.badlogic.gdx.maps.tiled;

import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.maps.ImageResolver;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.XmlReader.Element;

public abstract class BaseTideMapLoader<T extends TiledMap, P extends AssetLoaderParameters<T>> extends BaseTiledMapLoader<T, P> {

	public BaseTideMapLoader () {
		super(new InternalFileHandleResolver());
	}

	public BaseTideMapLoader (FileHandleResolver resolver) {
		super(resolver);
	}

	// callbacks

	public abstract T createTiledMap ();

	public abstract boolean isYUp ();

	public abstract void populateWithTiles (TiledMapTileSet tileset, T map, FileHandle mapFile, FileHandle tilesetImage);

	@Override
	public void finishLoading (P parameters) {
	};

	/** Loads the map data, given the XML root element and an {@link ImageResolver} used to return the tileset Textures
	 * @param root the XML root element
	 * @param mapFile the Filehandle of the tmx file
	 * @return the {@link TiledMap} */
	@Override
	public T loadTilemap (Element root, FileHandle mapFile) {
		T map = createTiledMap();

		Element properties = root.getChildByName("properties");
		if (properties != null) {
			loadProperties(map.getProperties(), properties);
		}
		Element tilesheets = root.getChildByName("TileSheets");
		for (Element tilesheet : tilesheets.getChildrenByName("TileSheet")) {
			loadTileSheet(map, tilesheet, mapFile);
		}
		Element layers = root.getChildByName("Layers");
		for (Element layer : layers.getChildrenByName("Layer")) {
			loadLayer(map, layer);
		}

		return map;
	}

	private void loadTileSheet (T map, Element element, FileHandle mapFile) {
		if (element.getName().equals("TileSheet")) {
			String id = element.getAttribute("Id");
			String description = element.getChildByName("Description").getText();
			String imageSource = element.getChildByName("ImageSource").getText();

			Element alignment = element.getChildByName("Alignment");
			String sheetSize = alignment.getAttribute("SheetSize");
			String tileSize = alignment.getAttribute("TileSize");
			String margin = alignment.getAttribute("Margin");
			String spacing = alignment.getAttribute("Spacing");

			String[] sheetSizeParts = sheetSize.split(" x ");
			int sheetSizeX = Integer.parseInt(sheetSizeParts[0]);
			int sheetSizeY = Integer.parseInt(sheetSizeParts[1]);

			String[] tileSizeParts = tileSize.split(" x ");
			int tileSizeX = Integer.parseInt(tileSizeParts[0]);
			int tileSizeY = Integer.parseInt(tileSizeParts[1]);

			String[] marginParts = margin.split(" x ");
			int marginX = Integer.parseInt(marginParts[0]);
			int marginY = Integer.parseInt(marginParts[1]);

			String[] spacingParts = margin.split(" x ");
			int spacingX = Integer.parseInt(spacingParts[0]);
			int spacingY = Integer.parseInt(spacingParts[1]);

			FileHandle image = getRelativeFileHandle(mapFile, imageSource);

			// TODO: Actually load the tilesheet
			// Need to make global ids as Tide doesn't have global ids.
			TiledMapTileSets tilesets = map.getTileSets();
			int firstgid = 1;
			for (TiledMapTileSet tileset : tilesets) {
				firstgid += tileset.size();
			}

			TiledMapTileSet tileset = new TiledMapTileSet();
			tileset.setName(id);
			tileset.getProperties().put("firstgid", firstgid);
			tileset.getProperties().put("tilewidth", tileSizeX);
			tileset.getProperties().put("tileheight", tileSizeY);
			tileset.getProperties().put("spacingX", spacingX);
			tileset.getProperties().put("spacingY", spacingY);
			tileset.getProperties().put("marginX", marginX);
			tileset.getProperties().put("marginY", marginY);
			int gid = firstgid;

			populateWithTiles(tileset, map, mapFile, image);

			Element properties = element.getChildByName("Properties");
			if (properties != null) {
				loadProperties(tileset.getProperties(), properties);
			}

			tilesets.addTileSet(tileset);
		}
	}

	private void loadLayer (TiledMap map, Element element) {
		if (element.getName().equals("Layer")) {
			String id = element.getAttribute("Id");
			String visible = element.getAttribute("Visible");

			Element dimensions = element.getChildByName("Dimensions");
			String layerSize = dimensions.getAttribute("LayerSize");
			String tileSize = dimensions.getAttribute("TileSize");

			String[] layerSizeParts = layerSize.split(" x ");
			int layerSizeX = Integer.parseInt(layerSizeParts[0]);
			int layerSizeY = Integer.parseInt(layerSizeParts[1]);

			String[] tileSizeParts = tileSize.split(" x ");
			int tileSizeX = Integer.parseInt(tileSizeParts[0]);
			int tileSizeY = Integer.parseInt(tileSizeParts[1]);

			TiledMapTileLayer layer = new TiledMapTileLayer(layerSizeX, layerSizeY, tileSizeX, tileSizeY);
			Element tileArray = element.getChildByName("TileArray");
			Array<Element> rows = tileArray.getChildrenByName("Row");
			TiledMapTileSets tilesets = map.getTileSets();
			TiledMapTileSet currentTileSet = null;
			int firstgid = 0;
			int x, y;
			for (int row = 0, rowCount = rows.size; row < rowCount; row++) {
				Element currentRow = rows.get(row);
				y = rowCount - 1 - row;
				x = 0;
				for (int child = 0, childCount = currentRow.getChildCount(); child < childCount; child++) {
					Element currentChild = currentRow.getChild(child);
					String name = currentChild.getName();
					if (name.equals("TileSheet")) {
						currentTileSet = tilesets.getTileSet(currentChild.getAttribute("Ref"));
						firstgid = currentTileSet.getProperties().get("firstgid", Integer.class);
					} else if (name.equals("Null")) {
						x += currentChild.getIntAttribute("Count");
					} else if (name.equals("Static")) {
						Cell cell = new Cell();
						cell.setTile(currentTileSet.getTile(firstgid + currentChild.getIntAttribute("Index")));
						layer.setCell(x++, y, cell);
					} else if (name.equals("Animated")) {
						// Create an AnimatedTile
						int interval = currentChild.getInt("Interval");
						Element frames = currentChild.getChildByName("Frames");
						Array<StaticTiledMapTile> frameTiles = new Array<StaticTiledMapTile>();
						for (int frameChild = 0, frameChildCount = frames.getChildCount(); frameChild < frameChildCount; frameChild++) {
							Element frame = frames.getChild(frameChild);
							String frameName = frame.getName();
							if (frameName.equals("TileSheet")) {
								currentTileSet = tilesets.getTileSet(frame.getAttribute("Ref"));
								firstgid = currentTileSet.getProperties().get("firstgid", Integer.class);
							} else if (frameName.equals("Static")) {
								frameTiles.add((StaticTiledMapTile)currentTileSet.getTile(firstgid + frame.getIntAttribute("Index")));
							}
						}
						Cell cell = new Cell();
						cell.setTile(new AnimatedTiledMapTile(interval / 1000f, frameTiles));
						layer.setCell(x++, y, cell); // TODO: Reuse existing animated tiles
					}
				}
			}
			map.getLayers().add(layer);
		}
	}
}
