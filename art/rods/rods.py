"""
The six rods as 3D item models (see rods3d.py; needs Blockbench running with its MCP plugin).

    python3 rods.py [id ...]       (writes models/, textures/, items/, bbmodel/, renders/, sheet.png)
"""
import rods3d

if __name__ == "__main__":
    rods3d.main()
